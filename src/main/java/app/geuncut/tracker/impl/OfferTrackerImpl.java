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
			int totalQuantity,
			int price,
			Instant now) {
		if (state == GrandExchangeOfferState.EMPTY) {
			return residualFill(slots.remove(slot), slot, now);
		}

		boolean selling = isSellState(state);
		boolean firstSinceReset = replayedSlots.add(slot);
		OfferSnapshot last = slots.get(slot);
		slots.put(slot, new OfferSnapshot(itemId, quantitySold, spent, state, totalQuantity, price));

		if (last == null || last.getItemId() != itemId || isSellState(last.getState()) != selling) {
			if (quantitySold > 0 && firstSinceReset) {
				return Optional.empty();
			}
			last = new OfferSnapshot(itemId, 0, 0, state, totalQuantity, price);
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

	// A slot that empties while its offer was still BUYING/SELLING and short of
	// its total means the final fill's event never arrived - it can land in the
	// same tick as the collection and collapse straight to EMPTY. Book the
	// unaccounted units at the offer price rather than losing them. An abort
	// always shows its CANCELLED state first, so those clear with no residual;
	// a slot collected while this client was away has no snapshot and is skipped.
	private Optional<OfferDelta> residualFill(OfferSnapshot last, int slot, Instant now) {
		if (last == null || !isActiveState(last.getState())) {
			return Optional.empty();
		}
		int missing = last.getQuantityTotal() - last.getQuantitySold();
		if (missing <= 0 || last.getPrice() <= 0) {
			return Optional.empty();
		}
		OfferDelta.Side side = isSellState(last.getState()) ? OfferDelta.Side.SELL : OfferDelta.Side.BUY;
		log.debug("event=residual_fill_on_empty item={} slot={} missing={} price={}",
				last.getItemId(), slot, missing, last.getPrice());
		return Optional.of(new OfferDelta(last.getItemId(), side, missing, last.getPrice(), slot, now));
	}

	@Override
	public void reset() {
		slots.clear();
		replayedSlots.clear();
	}

	private static boolean isActiveState(GrandExchangeOfferState state) {
		return state == GrandExchangeOfferState.BUYING
				|| state == GrandExchangeOfferState.SELLING;
	}

	private static boolean isSellState(GrandExchangeOfferState state) {
		return state == GrandExchangeOfferState.SELLING
				|| state == GrandExchangeOfferState.SOLD
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}
}
