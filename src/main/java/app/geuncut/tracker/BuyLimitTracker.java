package app.geuncut.tracker;

import java.time.Instant;
import java.util.Optional;

public interface BuyLimitTracker {
	void recordBuy(int itemId, int quantity, Instant boughtAt);

	int boughtInWindow(int itemId, Instant now);

	Optional<Instant> nextReset(int itemId, Instant now);

	void reset();
}
