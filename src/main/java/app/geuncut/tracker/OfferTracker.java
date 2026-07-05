package app.geuncut.tracker;

import java.time.Instant;
import java.util.Optional;

import app.geuncut.model.OfferDelta;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Turns raw GrandExchangeOfferChanged events into fill deltas.
 */
public interface OfferTracker {
	Optional<OfferDelta> onOfferChanged(
			int slot,
			int itemId,
			GrandExchangeOfferState state,
			int quantitySold,
			int spent,
			Instant now);

	void reset();
}
