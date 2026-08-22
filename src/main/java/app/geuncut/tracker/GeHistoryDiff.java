package app.geuncut.tracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeTradeEvent;

public final class GeHistoryDiff {
	private static final int PRICE_TOLERANCE = 1;

	private GeHistoryDiff() {
	}

	public static List<GeHistoryRow> unseen(List<GeHistoryRow> rows, List<GeTradeEvent> logged) {
		List<GeHistoryRow> missing = new ArrayList<>();
		if (rows == null || rows.isEmpty()) {
			return missing;
		}
		List<Offer> offers = offers(logged);
		boolean[] taken = new boolean[offers.size()];
		for (GeHistoryRow row : rows) {
			int match = -1;
			for (int index = 0; index < offers.size() && match < 0; index++) {
				if (!taken[index] && matches(row, offers.get(index))) {
					match = index;
				}
			}
			if (match >= 0) {
				taken[match] = true;
			} else {
				missing.add(row);
			}
		}
		return missing;
	}

	private static boolean matches(GeHistoryRow row, Offer offer) {
		if (row.getItemId() != offer.itemId || !offer.side.equals(row.getSide())
				|| row.getQuantity() != offer.quantity) {
			return false;
		}
		return within(row.getPriceEach(), offer.priceEach)
				|| within(row.getPriceEach(), GeTax.afterTax(offer.priceEach));
	}

	private static boolean within(int shown, int known) {
		return Math.abs((long) shown - known) <= PRICE_TOLERANCE;
	}

	private static List<Offer> offers(List<GeTradeEvent> logged) {
		Map<String, Offer> grouped = new LinkedHashMap<>();
		if (logged == null) {
			return new ArrayList<>();
		}
		for (GeTradeEvent event : logged) {
			if (event == null || event.getSide() == null || event.getQuantity() <= 0) {
				continue;
			}
			String key = event.getOfferInstanceId() != null
					? event.getOfferInstanceId()
					: event.getItemId() + ":" + event.getSide();
			Offer offer = grouped.computeIfAbsent(key, unused -> new Offer(event.getItemId(), event.getSide()));
			offer.quantity += event.getQuantity();
			offer.value += (long) event.getQuantity() * event.getPriceEach();
		}
		List<Offer> offers = new ArrayList<>(grouped.size());
		for (Offer offer : grouped.values()) {
			if (offer.quantity > 0) {
				offer.priceEach = (int) Math.min(Integer.MAX_VALUE,
						Math.round((double) offer.value / offer.quantity));
				offers.add(offer);
			}
		}
		return offers;
	}

	private static final class Offer {
		private final int itemId;
		private final String side;
		private long quantity;
		private long value;
		private int priceEach;

		private Offer(int itemId, String side) {
			this.itemId = itemId;
			this.side = side;
		}
	}
}
