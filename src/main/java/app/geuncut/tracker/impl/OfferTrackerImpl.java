package app.geuncut.tracker.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;

import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.OfferTracker;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Turns raw GrandExchangeOfferChanged events into fill deltas.
 *
 * The client replays the state of every slot on login. The first event seen
 * for a slot therefore only establishes a baseline and never emits a delta;
 * without this, every login would double count the standing offers.
 */
@Singleton
public class OfferTrackerImpl implements OfferTracker {
	private final Map<Integer, OfferSnapshot> slots = new HashMap<>();

	@Override
	public Optional<OfferDelta> onOfferChanged(
			int slot,
			int itemId,
			GrandExchangeOfferState state,
			int quantitySold,
			int spent,
			Instant now) {
		if (state == GrandExchangeOfferState.EMPTY) {
			slots.remove(slot);
			return Optional.empty();
		}

		boolean selling = isSellState(state);
		OfferSnapshot last = slots.get(slot);
		slots.put(slot, new OfferSnapshot(itemId, quantitySold, spent, selling));

		if (last == null || last.getItemId() != itemId || last.isSelling() != selling) {
			if (quantitySold > 0 && last == null) {
				return Optional.empty();
			}
			last = new OfferSnapshot(itemId, 0, 0, selling);
		}

		int quantityDelta = quantitySold - last.getQuantitySold();
		int spentDelta = spent - last.getSpent();
		if (quantityDelta <= 0 || spentDelta <= 0) {
			return Optional.empty();
		}

		int priceEach = Math.round((float) spentDelta / quantityDelta);
		OfferDelta.Side side = selling ? OfferDelta.Side.SELL : OfferDelta.Side.BUY;
		return Optional.of(new OfferDelta(itemId, side, quantityDelta, priceEach, slot, now));
	}

	@Override
	public void reset() {
		slots.clear();
	}

	private static boolean isSellState(GrandExchangeOfferState state) {
		return state == GrandExchangeOfferState.SELLING
				|| state == GrandExchangeOfferState.SOLD
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}
}
