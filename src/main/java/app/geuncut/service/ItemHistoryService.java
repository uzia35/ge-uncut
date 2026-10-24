package app.geuncut.service;

import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.ItemHistory;
import lombok.Value;

/** One active item, with bounded polling and generation checks across close/reopen and A/B/A searches. */
@Singleton
public class ItemHistoryService {
	private static final long REFRESH_MS = 60_000;
	private final GeUncutApi api;
	private final LongSupplier clock;
	private long generation;
	private long completedAt;
	private Snapshot snapshot = new Snapshot(-1, Status.IDLE, null);

	public enum Status { IDLE, LOADING, READY, ERROR }

	@Value
	public static class Snapshot {
		int itemId;
		Status status;
		ItemHistory history;
	}

	@Inject
	public ItemHistoryService(GeUncutApi api) {
		this(api, System::currentTimeMillis);
	}

	ItemHistoryService(GeUncutApi api, LongSupplier clock) {
		this.api = api;
		this.clock = clock;
	}

	public synchronized Snapshot select(int itemId) {
		if (snapshot.itemId != itemId
				|| (snapshot.status == Status.READY && clock.getAsLong() - completedAt >= REFRESH_MS)) {
			load(itemId);
		}
		return snapshot;
	}

	public synchronized void retry(int itemId) {
		if (snapshot.itemId == itemId && snapshot.status == Status.ERROR) {
			load(itemId);
		}
	}

	public synchronized void clear() {
		if (snapshot.status != Status.IDLE) {
			generation++;
			snapshot = new Snapshot(-1, Status.IDLE, null);
		}
	}

	private void load(int itemId) {
		long request = ++generation;
		snapshot = new Snapshot(itemId, Status.LOADING, null);
		api.fetchItemHistory(itemId,
				history -> complete(request, itemId, history == null ? Status.ERROR : Status.READY, history),
				failure -> complete(request, itemId, Status.ERROR, null));
	}

	private synchronized void complete(long request, int itemId, Status status, ItemHistory history) {
		if (request == generation && snapshot.itemId == itemId) {
			completedAt = clock.getAsLong();
			snapshot = new Snapshot(itemId, status, history);
		}
	}
}
