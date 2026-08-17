package app.geuncut.tracker.impl;

import java.util.HashMap;
import java.util.Map;

import app.geuncut.model.OfferSnapshot;
import app.geuncut.tracker.SnapshotStore;

public class InMemorySnapshotStore implements SnapshotStore {
	private final Map<String, Map<Integer, OfferSnapshot>> data = new HashMap<>();

	@Override
	public Map<Integer, OfferSnapshot> load(String accountHash) {
		Map<Integer, OfferSnapshot> slots = data.get(accountHash);
		return slots != null ? new HashMap<>(slots) : new HashMap<>();
	}

	@Override
	public void save(String accountHash, Map<Integer, OfferSnapshot> slots) {
		if (accountHash == null) {
			return;
		}
		if (slots.isEmpty()) {
			data.remove(accountHash);
		} else {
			data.put(accountHash, new HashMap<>(slots));
		}
	}
}
