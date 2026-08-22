package app.geuncut.tracker;

import app.geuncut.dto.GeTradeEvent;

public interface FillLog {
	void append(String accountHash, GeTradeEvent event);

	FillBatch read(String accountHash, long offset, int maxEntries);

	long deliveredOffset(String accountHash);

	void markDelivered(String accountHash, long offset);
}
