package app.geuncut.service.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
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
	private final AtomicBoolean posting = new AtomicBoolean();
	private final Map<String, long[]> replays = new ConcurrentHashMap<>();
	private volatile String lastAccount;
	private volatile int batchSize = MAX_BATCH_STATES;
	private volatile int unauthorizedQuietTicks;

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
	}

	@Override
	protected void flush() {
		String current = accountHash();
		if (current != null) {
			lastAccount = current;
		}
		String accountHash = current != null ? current : lastAccount;
		if (accountHash == null || posting.get() || !sendGate.getAsBoolean()) {
			return;
		}
		if (unauthorizedQuietTicks > 0) {
			unauthorizedQuietTicks--;
			return;
		}
		long[] replay = replays.computeIfAbsent(accountHash, key -> {
			long through = offerLog.deliveredOffset(key);
			log.debug("event=offer_state_sync_replay_started account={} through={}", key, through);
			return new long[] { 0, through };
		});
		OfferBatch fresh = offerLog.read(accountHash, offerLog.deliveredOffset(accountHash), batchSize);
		if (!fresh.getEntries().isEmpty()) {
			send(accountHash, fresh, null);
			return;
		}
		if (replay[0] < replay[1]) {
			OfferBatch old = offerLog.read(accountHash, replay[0], batchSize);
			if (old.getEntries().isEmpty()) {
				replay[0] = replay[1];
				return;
			}
			send(accountHash, old, replay);
		}
	}

	private void send(String accountHash, OfferBatch chunk, long[] replay) {
		if (!posting.compareAndSet(false, true)) {
			return;
		}
		List<OfferState> batch = chunk.getEntries();
		long next = chunk.getNextOffset();
		api.postOfferStates(accountHash, batch,
				result -> {
					unauthorizedQuietTicks = 0;
					batchSize = MAX_BATCH_STATES;
					passed(accountHash, next, replay);
					posting.set(false);
					int fills = result != null ? result.getFills() : 0;
					log.debug("event=offer_state_sync_flushed count={} replay={} offset={} fills={}",
							batch.size(), replay != null, next, fills);
					if (fills > 0) {
						onFillsBooked.accept(fills);
					}
				},
				failure -> {
					if (failure.isUnauthorized()) {
						unauthorizedQuietTicks = UNAUTHORIZED_QUIET_TICKS;
						log.warn("event=offer_state_sync_unauthorized held={}", batch.size());
					} else if (rejected(failure)) {
						if (batch.size() > 1) {
							batchSize = 1;
						} else {
							log.warn("event=offer_state_sync_rejected slot={} state={} status={}",
									batch.get(0).getSlot(), batch.get(0).getState(), failure.getStatusCode());
							passed(accountHash, next, replay);
						}
					} else {
						log.debug("event=offer_state_sync_retry count={} kind={} status={}",
								batch.size(), failure.getKind(), failure.getStatusCode());
					}
					posting.set(false);
				});
	}

	private void passed(String accountHash, long next, long[] replay) {
		if (replay != null) {
			replay[0] = next;
		} else {
			offerLog.markDelivered(accountHash, next);
		}
	}

	private static boolean rejected(ApiFailure failure) {
		int status = failure.getStatusCode();
		return failure.getKind() == ApiFailure.Kind.HTTP && status >= 400 && status < 500
				&& status != 408 && status != 429;
	}
}