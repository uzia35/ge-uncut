package app.geuncut.service;

import java.util.function.BooleanSupplier;

import app.geuncut.model.OfferDelta;

public interface TradeSyncService extends SyncService {
	void accept(OfferDelta delta);

	void setSendGate(BooleanSupplier sendGate);

	void setInstallId(String installId);
}
