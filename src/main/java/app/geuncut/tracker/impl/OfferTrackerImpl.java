package app.geuncut.tracker.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.model.OfferDelta;
import app.geuncut.model.OfferSnapshot;
import app.geuncut.tracker.OfferTracker;
import app.geuncut.tracker.SnapshotStore;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;

@Slf4j
@Singleton
public class OfferTrackerImpl implements OfferTracker {
	private final Map<Integer, OfferSnapshot> slots = new HashMap<>();
	private final Set<Integer> replayedSlots = new HashSet<>();
	private final SnapshotStore store;
	private String currentAccount;

	public OfferTrackerImpl() {
		this(new InMemorySnapshotStore());
	}

	@Inject
	public OfferTrackerImpl(SnapshotStore store) {
		this.store = store;
	}

	@Override
	public void onOfferChanged(
			int slot,
			int itemId,
			GrandExchangeOfferState state,
			int quantitySold,
			int spent,
			int totalQuantity,
			int price,
			Instant now,
			Consumer<OfferDelta> onFill) {
		OfferDelta delta = advance(slot, itemId, state, quantitySold, spent, totalQuantity, price, now);
		if (delta != null && onFill != null) {
			onFill.accept(delta);
		}
		persist();
	}

	private OfferDelta advance(
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
		boolean newOffer = last == null
				|| last.getItemId() != itemId
				|| isSellState(last.getState()) != selling
				|| quantitySold < last.getQuantitySold()
				|| last.getOfferId() == null;
		String offerId = newOffer ? UUID.randomUUID().toString() : last.getOfferId();
		slots.put(slot, new OfferSnapshot(itemId, quantitySold, spent, state, totalQuantity, price, offerId));

		if (last == null || last.getItemId() != itemId || isSellState(last.getState()) != selling) {
			if (quantitySold > 0 && firstSinceReset) {
				return null;
			}
			last = new OfferSnapshot(itemId, 0, 0, state, totalQuantity, price, offerId);
		}

		int quantityDelta = quantitySold - last.getQuantitySold();
		int spentDelta = spent - last.getSpent();
		if (quantityDelta <= 0 || spentDelta <= 0) {
			if (quantityDelta > 0 && spentDelta < 0) {
				log.warn("event=fill_discarded_negative_spend item={} slot={} quantity_delta={} spent_delta={}",
						itemId, slot, quantityDelta, spentDelta);
			}
			return null;
		}

		int priceEach = (int) Math.round((double) spentDelta / quantityDelta);
		OfferDelta.Side side = selling ? OfferDelta.Side.SELL : OfferDelta.Side.BUY;
		return new OfferDelta(itemId, side, quantityDelta, priceEach, slot, now, offerId, quantitySold);
	}

	private OfferDelta residualFill(OfferSnapshot last, int slot, Instant now) {
		if (last == null || !isActiveState(last.getState())) {
			return null;
		}
		int missing = last.getQuantityTotal() - last.getQuantitySold();
		if (missing <= 0 || last.getPrice() <= 0) {
			return null;
		}
		OfferDelta.Side side = isSellState(last.getState()) ? OfferDelta.Side.SELL : OfferDelta.Side.BUY;
		String offerId = last.getOfferId() != null ? last.getOfferId() : UUID.randomUUID().toString();
		log.debug("event=residual_fill_on_empty item={} slot={} missing={} price={}",
				last.getItemId(), slot, missing, last.getPrice());
		return new OfferDelta(last.getItemId(), side, missing, last.getPrice(), slot, now,
				offerId, last.getQuantityTotal());
	}

	@Override
	public void loadFor(String accountHash) {
		currentAccount = accountHash;
		slots.clear();
		if (accountHash != null) {
			slots.putAll(store.load(accountHash));
		}
	}

	@Override
	public void reset() {
		slots.clear();
		replayedSlots.clear();
	}

	private void persist() {
		if (currentAccount != null) {
			store.save(currentAccount, slots);
		}
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
