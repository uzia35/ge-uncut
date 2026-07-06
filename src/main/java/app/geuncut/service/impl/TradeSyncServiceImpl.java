package app.geuncut.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Deque;
import java.util.function.Supplier;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.config.GeUncutConfig;
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
public class TradeSyncServiceImpl implements TradeSyncService {
	private static final int FLUSH_SECONDS = 5;
	private static final int MAX_QUEUED = 200;

	private final GeUncutApi api;
	private final GeUncutConfig config;
	private final ScheduledExecutorService executor;
	private final Deque<GeTradeEvent> queue = new ArrayDeque<>();

	private ScheduledFuture<?> flusher;
	private Supplier<String> accountHashSupplier;

	@Inject
	public TradeSyncServiceImpl(GeUncutApi api, GeUncutConfig config, ScheduledExecutorService executor) {
		this.api = api;
		this.config = config;
		this.executor = executor;
	}

	@Override
	public void start(Supplier<String> accountHashSupplier) {
		this.accountHashSupplier = accountHashSupplier;
		flusher = executor.scheduleWithFixedDelay(this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	public void stop() {
		if (flusher != null) {
			flusher.cancel(false);
			flusher = null;
		}
		flush();
		synchronized (queue) {
			queue.clear();
		}
	}

	@Override
	public void accept(OfferDelta delta) {
		if (!config.syncTrades()) {
			return;
		}
		GeTradeEvent payload = toPayload(delta);
		synchronized (queue) {
			if (queue.size() >= MAX_QUEUED) {
				queue.removeFirst();
			}
			queue.addLast(payload);
		}
	}

	private void flush() {
		List<GeTradeEvent> batch;
		synchronized (queue) {
			if (queue.isEmpty()) {
				return;
			}
			batch = new ArrayList<>(queue);
			queue.clear();
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
					}
				});
	}

	private GeTradeEvent toPayload(OfferDelta delta) {
		String accountHash = accountHashSupplier.get();
		return GeTradeEvent.builder()
				.accountHash(accountHash)
				.idempotencyKey(idempotencyKey(accountHash, delta))
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
