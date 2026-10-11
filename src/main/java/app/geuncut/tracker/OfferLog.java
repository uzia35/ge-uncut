package app.geuncut.tracker;

import app.geuncut.dto.OfferState;

public interface OfferLog {
	void append(String accountHash, OfferState state);

	OfferBatch read(String accountHash, long offset, int maxEntries);

	long deliveredOffset(String accountHash);

	void markDelivered(String accountHash, long offset);
}
