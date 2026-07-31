package app.geuncut.service;

import app.geuncut.model.OfferDelta;

public interface TradeSyncService extends SyncService {
	void accept(OfferDelta delta);
}
