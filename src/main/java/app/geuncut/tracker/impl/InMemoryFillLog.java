package app.geuncut.tracker.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.FillLog;

public class InMemoryFillLog implements FillLog {
	private final Map<String, LinkedHashMap<String, GeTradeEvent>> byAccount = new HashMap<>();

	@Override
	public synchronized void append(String accountHash, GeTradeEvent event) {
		if (accountHash == null || event == null || event.getIdempotencyKey() == null) {
			return;
		}
		byAccount.computeIfAbsent(accountHash, key -> new LinkedHashMap<>())
				.put(event.getIdempotencyKey(), event);
	}

	@Override
	public synchronized List<GeTradeEvent> pending(String accountHash) {
		LinkedHashMap<String, GeTradeEvent> log = byAccount.get(accountHash);
		return log == null ? new ArrayList<>() : new ArrayList<>(log.values());
	}

	@Override
	public synchronized void ack(String accountHash, Collection<String> idempotencyKeys) {
		LinkedHashMap<String, GeTradeEvent> log = byAccount.get(accountHash);
		if (log != null) {
			idempotencyKeys.forEach(log::remove);
		}
	}
}
