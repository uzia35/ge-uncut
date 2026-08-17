package app.geuncut.tracker;

import java.util.Map;

import app.geuncut.model.OfferSnapshot;

public interface SnapshotStore {
	Map<Integer, OfferSnapshot> load(String accountHash);

	void save(String accountHash, Map<Integer, OfferSnapshot> slots);
}
