package app.geuncut.tracker;

import java.time.Instant;
import java.util.Optional;

/**
 * Rolling 4 hour purchase window per item, mirroring the GE buy limit rules.
 */
public interface BuyLimitTracker {
	void recordBuy(int itemId, int quantity, Instant boughtAt);

	int boughtInWindow(int itemId, Instant now);

	/**
	 * When the oldest purchase in the window rolls off, freeing limit again.
	 */
	Optional<Instant> nextReset(int itemId, Instant now);

	void reset();
}
