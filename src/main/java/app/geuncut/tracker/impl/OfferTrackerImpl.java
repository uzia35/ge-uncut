package app.geuncut.tracker.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
			OfferSnapshot removed = slots.remove(slot);
			persist();
			return residualFill(removed, slot, now);
		}

		boolean selling = isSellState(state);
		boolean firstSinceReset = replayedSlots.add(slot);
		OfferSnapshot last = slots.get(slot);
		slots.put(slot, new OfferSnapshot(itemId, quantitySold, spent, state, totalQuantity, price));
		persist();

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
