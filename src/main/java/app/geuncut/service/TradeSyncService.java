package app.geuncut.service;

import java.util.function.Supplier;

import app.geuncut.model.OfferDelta;

/**
 * Reports observed GE fills to geuncut.app.
 */
public interface TradeSyncService {
	void start(Supplier<String> accountHashSupplier);

	void stop();

	void accept(OfferDelta delta);
}
