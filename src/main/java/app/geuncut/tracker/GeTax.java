package app.geuncut.tracker;

public final class GeTax {
	private static final int EXEMPT_BELOW = 50;
	private static final long PER_ITEM_CAP = 5_000_000L;

	private GeTax() {
	}

	public static int perItem(int priceEach) {
		if (priceEach < EXEMPT_BELOW) {
			return 0;
		}
		return (int) Math.min((long) priceEach * 2 / 100, PER_ITEM_CAP);
	}

	public static int afterTax(int priceEach) {
		return priceEach - perItem(priceEach);
	}

	public static int beforeTax(int priceAfterTax) {
		if (priceAfterTax < EXEMPT_BELOW) {
			return priceAfterTax;
		}
		long estimate = Math.round(priceAfterTax / 0.98);
		for (long candidate = Math.max(0, estimate - 3); candidate <= estimate + 3; candidate++) {
			if (candidate <= Integer.MAX_VALUE && afterTax((int) candidate) == priceAfterTax) {
				return (int) candidate;
			}
		}
		long capped = (long) priceAfterTax + PER_ITEM_CAP;
		if (capped <= Integer.MAX_VALUE && afterTax((int) capped) == priceAfterTax) {
			return (int) capped;
		}
		return (int) Math.min(estimate, Integer.MAX_VALUE);
	}
}
