package app.geuncut.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.TradeSyncService;
import app.geuncut.tracker.FillLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TradeSyncServiceImpl extends AbstractSyncService implements TradeSyncService {
	private static final int FLUSH_SECONDS = 5;
	private static final int MAX_BATCH_EVENTS = 50;

	private final GeUncutApi api;
	private final FillLog fillLog;

	@Inject
	public TradeSyncServiceImpl(GeUncutApi api, FillLog fillLog, ScheduledExecutorService executor) {
		super(executor, FLUSH_SECONDS);
		this.api = api;
		this.fillLog = fillLog;
	}

	@Override
	protected void onStop() {
		flush();
	}

	@Override
	public void accept(OfferDelta delta) {
		String accountHash = accountHash();
		if (accountHash == null) {
			return;
		}
		fillLog.append(accountHash, toPayload(accountHash, delta));
	}

	@Override
	protected void flush() {
		String accountHash = accountHash();
		if (accountHash == null) {
			return;
		}
		List<GeTradeEvent> pending = fillLog.pending(accountHash);
		if (pending.isEmpty()) {
			return;
		}
		if (pending.size() > MAX_BATCH_EVENTS) {
			pending = pending.subList(0, MAX_BATCH_EVENTS);
		}
		String publishedAt = Instant.now().toString();
		List<GeTradeEvent> batch = new ArrayList<>(pending.size());
		List<String> keys = new ArrayList<>(pending.size());
		for (GeTradeEvent event : pending) {
			batch.add(event.toBuilder().publishedAt(publishedAt).build());
			keys.add(event.getIdempotencyKey());
		}
		api.postGeEvents(batch,
				() -> {
					fillLog.ack(accountHash, keys);
					log.debug("event=trade_sync_flushed count={}", batch.size());
				},
				failure -> {
					if (failure.isUnauthorized()) {
						fillLog.ack(accountHash, keys);
						log.warn("event=trade_sync_unauthorized dropped={}", batch.size());
						return;
					}
					log.debug("event=trade_sync_retry count={} kind={} status={}",
							batch.size(), failure.getKind(), failure.getStatusCode());
				});
	}

	private GeTradeEvent toPayload(String accountHash, OfferDelta delta) {
		String clientEventId = UUID.randomUUID().toString();
		return GeTradeEvent.builder()
				.accountHash(accountHash)
				.idempotencyKey(idempotencyKey(accountHash, delta) + ":" + clientEventId)
				.clientEventId(clientEventId)
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
