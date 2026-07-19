package app.geuncut.service.impl;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.TradeSyncService;
import lombok.extern.slf4j.Slf4j;

/**
 * Batches observed fills and reports them to geuncut.app. Failed batches are
 * requeued and retried on the next flush, so a network blip never loses a
 * fill; the server side dedupes on the idempotency key regardless.
 */
@Slf4j
@Singleton
public class TradeSyncServiceImpl extends AbstractSyncService implements TradeSyncService {
	private static final int FLUSH_SECONDS = 5;
	private static final int MAX_QUEUED = 200;

	private final GeUncutApi api;
	private final Deque<GeTradeEvent> queue = new ArrayDeque<>();

	@Inject
	public TradeSyncServiceImpl(GeUncutApi api, ScheduledExecutorService executor) {
		super(executor, FLUSH_SECONDS);
		this.api = api;
	}

	@Override
	protected void onStop() {
		flush();
		synchronized (queue) {
			queue.clear();
		}
	}

	@Override
	public void accept(OfferDelta delta) {
		GeTradeEvent payload = toPayload(delta);
		synchronized (queue) {
			if (queue.size() >= MAX_QUEUED) {
				queue.removeFirst();
			}
			queue.addLast(payload);
		}
	}

	@Override
	protected void flush() {
		List<GeTradeEvent> drained;
		synchronized (queue) {
			if (queue.isEmpty()) {
				return;
			}
			drained = new ArrayList<>(queue);
			queue.clear();
		}
		// Restamped per transmission attempt; client_event_id stays fixed, so
		// the pair separates "when it happened" from "when it finally got out".
		String publishedAt = Instant.now().toString();
		List<GeTradeEvent> batch = new ArrayList<>(drained.size());
		for (GeTradeEvent event : drained) {
			batch.add(event.toBuilder().publishedAt(publishedAt).build());
		}
		api.postGeEvents(batch,
				() -> log.debug("event=trade_sync_flushed count={}", batch.size()),
				failure -> {
					if (failure.isUnauthorized()) {
						// Not linked (yet, or anymore). Requeueing would retry the
						// same rejection every flush; the fills are dropped exactly
						// as they would be with sync disabled.
						log.warn("event=trade_sync_unauthorized dropped={}", batch.size());
						return;
					}
					log.debug("event=trade_sync_requeued count={} kind={} status={} reason=\"{}\"", batch.size(), failure.getKind(), failure.getStatusCode(), failure.getMessage());
					synchronized (queue) {
						for (int index = batch.size() - 1; index >= 0; index--) {
							queue.addFirst(batch.get(index));
						}
						// New fills may have arrived while the POST was in flight, so
						// re-apply the cap here too, dropping oldest first.
						while (queue.size() > MAX_QUEUED) {
							queue.removeFirst();
						}
					}
				});
	}

	private GeTradeEvent toPayload(OfferDelta delta) {
		String accountHash = accountHash();
		return GeTradeEvent.builder()
				.accountHash(accountHash)
				.idempotencyKey(idempotencyKey(accountHash, delta))
				.clientEventId(UUID.randomUUID().toString())
				.itemId(delta.getItemId())
				.side(delta.getSide() == OfferDelta.Side.BUY ? "buy" : "sell")
				.quantity(delta.getQuantity())
				.priceEach(delta.getPriceEach())
				.slot(delta.getSlot())
				.occurredAt(delta.getOccurredAt().toString())
				.build();
	}

	private static String idempotencyKey(String accountHash, OfferDelta delta) {
		return accountHash + ":" + delta.getSlot() + ":" + delta.getItemId() + ":"
				+ delta.getSide() + ":" + delta.getQuantity() + ":" + delta.getOccurredAt().toEpochMilli();
	}
}
