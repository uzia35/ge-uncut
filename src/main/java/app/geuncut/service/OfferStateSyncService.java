package app.geuncut.service;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public interface OfferStateSyncService extends SyncService {
	void setSendGate(BooleanSupplier sendGate);

	void setOnFillsBooked(IntConsumer onFillsBooked);
}
