package app.geuncut.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.TradeSyncService;
import app.geuncut.tracker.FillBatch;
import app.geuncut.tracker.FillLog;
import app.geuncut.tracker.GeHistoryDiff;
import app.geuncut.tracker.GeTax;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TradeSyncServiceImpl extends AbstractSyncService implements TradeSyncService {
	private static final int FLUSH_SECONDS = 5;
	private static final int MAX_BATCH_EVENTS = 50;
	private static final int UNAUTHORIZED_QUIET_TICKS = 60;
	private static final int IMPORTED_SLOT = 0;
	private static final String SOURCE_LIVE = "live";
	private static final String SOURCE_HISTORY = "history";

	private final GeUncutApi api;
	private final FillLog fillLog;

	private volatile BooleanSupplier sendGate = () -> true;
	private volatile String installId;
	private volatile boolean posting;
	private String replayedAccount;
	private long replayOffset;
	private long replayLimit;
	private int unauthorizedQuietTicks;

	@Inject
	public TradeSyncServiceImpl(GeUncutApi api, FillLog fillLog, ScheduledExecutorService executor) {
		super(executor, FLUSH_SECONDS);
		this.api = api;
		this.fillLog = fillLog;
	}

	@Override
	public void setSendGate(BooleanSupplier sendGate) {
		this.sendGate = sendGate != null ? sendGate : () -> true;
	}

	@Override
	public void setInstallId(String installId) {
		this.installId = installId;
	}

	@Override
	protected void onStop() {
		flush();
		replayedAccount = null;
	}

	@Override
	public void accept(OfferDelta delta) {
		String accountHash = accountHash();
		if (accountHash == null || delta == null) {
			return;
		}
		fillLog.append(accountHash, toPayload(accountHash, delta, SOURCE_LIVE));
	}

	@Override
	public int importHistory(List<GeHistoryRow> rows) {
		String accountHash = accountHash();
		if (accountHash == null || rows == null || rows.isEmpty()) {
			return 0;
		}
		List<GeTradeEvent> logged = fillLog.read(accountHash, 0, Integer.MAX_VALUE).getEntries();
		List<GeHistoryRow> unseen = GeHistoryDiff.unseen(rows, logged);
		Instant now = Instant.now();
		for (GeHistoryRow row : unseen) {
			OfferDelta delta = importedFill(row, now);
			if (delta != null) {
				fillLog.append(accountHash, toPayload(accountHash, delta, SOURCE_HISTORY));
			}
		}
		log.debug("event=history_diffed rows={} imported={}", rows.size(), unseen.size());
		return unseen.size();
	}

	@Override
	protected void flush() {
		String accountHash = accountHash();
		if (accountHash == null || posting || !sendGate.getAsBoolean()) {
			return;
		}
		if (unauthorizedQuietTicks > 0) {
			unauthorizedQuietTicks--;
			return;
		}
		if (!accountHash.equals(replayedAccount)) {
			replayedAccount = accountHash;
			replayOffset = 0;
			replayLimit = fillLog.deliveredOffset(accountHash);
			log.debug("event=trade_sync_replay_started account={} through={}", accountHash, replayLimit);
		}
		boolean replaying = replayOffset < replayLimit;
		send(accountHash, replaying ? replayOffset : fillLog.deliveredOffset(accountHash), replaying);
	}

	private void send(String accountHash, long offset, boolean replaying) {
		FillBatch chunk = fillLog.read(accountHash, offset, MAX_BATCH_EVENTS);
		if (chunk.getEntries().isEmpty()) {
			if (replaying) {
				replayOffset = replayLimit;
			}
			return;
		}
		String publishedAt = Instant.now().toString();
		List<GeTradeEvent> batch = new ArrayList<>(chunk.getEntries().size());
		for (GeTradeEvent event : chunk.getEntries()) {
			batch.add(event.toBuilder().publishedAt(publishedAt).build());
		}
		long next = chunk.getNextOffset();
		posting = true;
		api.postGeEvents(batch,
				() -> {
					posting = false;
					unauthorizedQuietTicks = 0;
					if (replaying) {
						replayOffset = next;
					}
					fillLog.markDelivered(accountHash, next);
					log.debug("event=trade_sync_flushed count={} replay={} offset={}",
							batch.size(), replaying, next);
				},
				failure -> {
					posting = false;
					if (failure.isUnauthorized()) {
						unauthorizedQuietTicks = UNAUTHORIZED_QUIET_TICKS;
						log.warn("event=trade_sync_unauthorized held={}", batch.size());
						return;
					}
					log.debug("event=trade_sync_retry count={} kind={} status={}",
							batch.size(), failure.getKind(), failure.getStatusCode());
				});
	}

	private static OfferDelta importedFill(GeHistoryRow row, Instant now) {
		if (row.getQuantity() <= 0 || row.getPriceEach() <= 0) {
			return null;
		}
		boolean selling = "sell".equals(row.getSide());
		int priceEach = selling ? GeTax.beforeTax(row.getPriceEach()) : row.getPriceEach();
		return new OfferDelta(row.getItemId(), selling ? OfferDelta.Side.SELL : OfferDelta.Side.BUY,
				row.getQuantity(), priceEach, IMPORTED_SLOT, now,
				UUID.randomUUID().toString(), row.getQuantity());
	}

	private GeTradeEvent toPayload(String accountHash, OfferDelta delta, String source) {
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
				.installId(installId)
				.source(source)
				.offerInstanceId(delta.getOfferId())
				.build();
	}

	private static String idempotencyKey(String accountHash, OfferDelta delta) {
		return accountHash + ":" + delta.getSlot() + ":" + delta.getOfferId()
				+ ":" + delta.getCumulativeQuantitySold();
	}
}
