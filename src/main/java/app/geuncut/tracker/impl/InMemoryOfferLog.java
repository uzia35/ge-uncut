package app.geuncut.tracker.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.geuncut.dto.OfferState;
import app.geuncut.tracker.OfferBatch;
import app.geuncut.tracker.OfferLog;

public class InMemoryOfferLog implements OfferLog {
	private final Map<String, List<OfferState>> byAccount = new HashMap<>();
	private final Map<String, Long> delivered = new HashMap<>();

	@Override
	public synchronized boolean append(String accountHash, OfferState state) {
		if (accountHash == null || state == null || state.getState() == null) {
			return false;
		}
		List<OfferState> entries = byAccount.computeIfAbsent(accountHash, key -> new ArrayList<>());
		entries.add(state.getSeq() != null ? state : state.toBuilder().seq((long) entries.size()).build());
		return true;
	}

	@Override
	public synchronized OfferBatch read(String accountHash, long offset, int maxEntries) {
		List<OfferState> entries = byAccount.get(accountHash);
		long start = Math.max(0, offset);
		if (entries == null || maxEntries <= 0 || start >= entries.size()) {
			return new OfferBatch(new ArrayList<>(), start);
		}
		int from = (int) start;
		int to = (int) Math.min(entries.size(), start + maxEntries);
		return new OfferBatch(new ArrayList<>(entries.subList(from, to)), to);
	}

	@Override
	public synchronized long deliveredOffset(String accountHash) {
		Long offset = delivered.get(accountHash);
		return offset != null ? offset : 0;
	}

	@Override
	public synchronized void markDelivered(String accountHash, long offset) {
		if (accountHash != null && offset > deliveredOffset(accountHash)) {
			delivered.put(accountHash, offset);
		}
	}
}
