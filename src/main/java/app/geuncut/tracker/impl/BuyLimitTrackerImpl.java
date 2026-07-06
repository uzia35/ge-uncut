package app.geuncut.tracker.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;

import app.geuncut.tracker.BuyLimitTracker;

@Singleton
public class BuyLimitTrackerImpl implements BuyLimitTracker {
	private static final Duration WINDOW = Duration.ofHours(4);

	private final Map<Integer, Deque<Purchase>> purchasesByItem = new HashMap<>();

	@Override
	public void recordBuy(int itemId, int quantity, Instant boughtAt) {
		purchasesByItem.computeIfAbsent(itemId, unseenItemId -> new ArrayDeque<>()).addLast(new Purchase(quantity, boughtAt));
	}

	@Override
	public int boughtInWindow(int itemId, Instant now) {
		Deque<Purchase> purchases = purchasesByItem.get(itemId);
		if (purchases == null) {
			return 0;
		}
		Instant cutoff = now.minus(WINDOW);
		while (!purchases.isEmpty() && purchases.peekFirst().getBoughtAt().isBefore(cutoff)) {
			purchases.removeFirst();
		}
		if (purchases.isEmpty()) {
			purchasesByItem.remove(itemId);
			return 0;
		}
		return purchases.stream().mapToInt(Purchase::getQuantity).sum();
	}

	@Override
	public Optional<Instant> nextReset(int itemId, Instant now) {
		Deque<Purchase> purchases = purchasesByItem.get(itemId);
		if (purchases == null || boughtInWindow(itemId, now) == 0) {
			return Optional.empty();
		}
		return Optional.of(purchases.peekFirst().getBoughtAt().plus(WINDOW));
	}

	@Override
	public void reset() {
		purchasesByItem.clear();
	}
}
