package app.geuncut.tracker.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.FillBatch;
import app.geuncut.tracker.FillLog;

public class InMemoryFillLog implements FillLog {
	private final Map<String, List<GeTradeEvent>> byAccount = new HashMap<>();
	private final Map<String, Long> delivered = new HashMap<>();

	@Override
	public synchronized void append(String accountHash, GeTradeEvent event) {
		if (accountHash == null || event == null || event.getIdempotencyKey() == null) {
			return;
		}
		List<GeTradeEvent> entries = byAccount.computeIfAbsent(accountHash, key -> new ArrayList<>());
		entries.add(event.toBuilder().seq((long) entries.size()).build());
	}

	@Override
	public synchronized FillBatch read(String accountHash, long offset, int maxEntries) {
		List<GeTradeEvent> entries = byAccount.get(accountHash);
		long start = Math.max(0, offset);
		if (entries == null || maxEntries <= 0 || start >= entries.size()) {
			return new FillBatch(new ArrayList<>(), start);
		}
		int from = (int) start;
		int to = (int) Math.min(entries.size(), start + maxEntries);
		return new FillBatch(new ArrayList<>(entries.subList(from, to)), to);
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
