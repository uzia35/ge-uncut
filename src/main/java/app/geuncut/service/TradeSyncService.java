package app.geuncut.service;

import app.geuncut.model.OfferDelta;

/**
 * Reports observed GE fills to geuncut.app.
 */
public interface TradeSyncService extends SyncService {
	void accept(OfferDelta delta);
}
