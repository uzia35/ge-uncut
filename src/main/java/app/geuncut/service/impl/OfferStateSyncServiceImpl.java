package app.geuncut.service.impl;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.OfferState;
import app.geuncut.service.OfferStateSyncService;
import app.geuncut.tracker.OfferBatch;
import app.geuncut.tracker.OfferLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class OfferStateSyncServiceImpl extends AbstractSyncService implements OfferStateSyncService {
	private static final int FLUSH_SECONDS = 5;
	private static final int MAX_BATCH_STATES = 200;
	private static final int UNAUTHORIZED_QUIET_TICKS = 60;

	private final GeUncutApi api;
	private final OfferLog offerLog;

	private volatile BooleanSupplier sendGate = () -> true;
	private volatile IntConsumer onFillsBooked = fills -> {};
	private volatile boolean posting;
	private String replayedAccount;
	private long replayOffset;
	private long replayLimit;
	private int unauthorizedQuietTicks;

	@Inject
	public OfferStateSyncServiceImpl(GeUncutApi api, OfferLog offerLog, ScheduledExecutorService executor) {
		super(executor, FLUSH_SECONDS);
		this.api = api;
		this.offerLog = offerLog;
	}

	@Override
	public void setSendGate(BooleanSupplier sendGate) {
		this.sendGate = sendGate != null ? sendGate : () -> true;
	}

	@Override
	public void setOnFillsBooked(IntConsumer onFillsBooked) {
		this.onFillsBooked = onFillsBooked != null ? onFillsBooked : fills -> {};
	}

	@Override
	protected void onStop() {
		flush();
		replayedAccount = null;
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
			replayLimit = offerLog.deliveredOffset(accountHash);
			log.debug("event=offer_state_sync_replay_started account={} through={}", accountHash, replayLimit);
		}
		boolean replaying = replayOffset < replayLimit;
		send(accountHash, replaying ? replayOffset : offerLog.deliveredOffset(accountHash), replaying);
	}

	private void send(String accountHash, long offset, boolean replaying) {
		OfferBatch chunk = offerLog.read(accountHash, offset, MAX_BATCH_STATES);
		if (chunk.getEntries().isEmpty()) {
			if (replaying) {
				replayOffset = replayLimit;
			}
			return;
		}
		List<OfferState> batch = chunk.getEntries();
		long next = chunk.getNextOffset();
		posting = true;
		api.postOfferStates(accountHash, batch,
				result -> {
					posting = false;
					unauthorizedQuietTicks = 0;
					if (replaying) {
						replayOffset = next;
					}
					offerLog.markDelivered(accountHash, next);
					int fills = result != null ? result.getFills() : 0;
					log.debug("event=offer_state_sync_flushed count={} replay={} offset={} fills={}",
							batch.size(), replaying, next, fills);
					if (fills > 0) {
						onFillsBooked.accept(fills);
					}
				},
				failure -> {
					posting = false;
					if (failure.isUnauthorized()) {
						unauthorizedQuietTicks = UNAUTHORIZED_QUIET_TICKS;
						log.warn("event=offer_state_sync_unauthorized held={}", batch.size());
						return;
					}
					log.debug("event=offer_state_sync_retry count={} kind={} status={}",
							batch.size(), failure.getKind(), failure.getStatusCode());
				});
	}
}
