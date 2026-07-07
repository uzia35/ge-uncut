package app.geuncut.tracker.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Singleton;

import app.geuncut.model.OfferDelta;
import app.geuncut.model.OfferSnapshot;
import app.geuncut.tracker.OfferTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Turns raw GrandExchangeOfferChanged events into fill deltas.
 *
 * The client replays the state of every slot on login. The first event seen
 * for a slot therefore only establishes a baseline and never emits a delta;
 * without this, every login would double count the standing offers.
 */
@Slf4j
@Singleton
public class OfferTrackerImpl implements OfferTracker {
	private final Map<Integer, OfferSnapshot> slots = new HashMap<>();
	// Slots whose first post-reset event has been seen. Only that first event is a
	// login/hop replay to baseline; a slot reused later in the same session is not.
	private final Set<Integer> replayedSlots = new HashSet<>();

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
		boolean firstSinceReset = replayedSlots.add(slot);
		OfferSnapshot last = slots.get(slot);
		slots.put(slot, new OfferSnapshot(itemId, quantitySold, spent, selling));

		if (last == null || last.getItemId() != itemId || last.isSelling() != selling) {
			if (quantitySold > 0 && firstSinceReset) {
				return Optional.empty();
			}
			last = new OfferSnapshot(itemId, 0, 0, selling);
		}

		int quantityDelta = quantitySold - last.getQuantitySold();
		int spentDelta = spent - last.getSpent();
		if (quantityDelta <= 0 || spentDelta <= 0) {
			if (quantityDelta > 0 && spentDelta < 0) {
				log.warn("event=fill_discarded_negative_spend item={} slot={} quantity_delta={} spent_delta={}",
						itemId, slot, quantityDelta, spentDelta);
			}
			return Optional.empty();
		}

		int priceEach = (int) Math.round((double) spentDelta / quantityDelta);
		OfferDelta.Side side = selling ? OfferDelta.Side.SELL : OfferDelta.Side.BUY;
		return Optional.of(new OfferDelta(itemId, side, quantityDelta, priceEach, slot, now));
	}

	@Override
	public void reset() {
		slots.clear();
		replayedSlots.clear();
	}

	private static boolean isSellState(GrandExchangeOfferState state) {
		return state == GrandExchangeOfferState.SELLING
				|| state == GrandExchangeOfferState.SOLD
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}
}
