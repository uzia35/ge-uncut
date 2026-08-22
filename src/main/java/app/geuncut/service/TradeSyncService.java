package app.geuncut.service;

import java.util.List;
import java.util.function.BooleanSupplier;

import app.geuncut.dto.GeHistoryRow;
import app.geuncut.model.OfferDelta;

public interface TradeSyncService extends SyncService {
	void accept(OfferDelta delta);

	int importHistory(List<GeHistoryRow> rows);

	void setSendGate(BooleanSupplier sendGate);

	void setInstallId(String installId);
}
