package app.geuncut.tracker;

import java.util.ArrayList;
import java.util.List;

import app.geuncut.dto.GeHistoryRow;
import net.runelite.api.widgets.Widget;

/**
 * Reads completed trades out of the in-game Grand Exchange trade history
 * window. Each entry is anchored by a "Bought:"/"Sold:" text child, followed
 * within a few children by the item (carries id + quantity) and a value text
 * ending in "= N each" ("105,350,000 coins (107,500,000 - 2,150,000) = 21,070
 * each"); single-unit rows only carry a "N coins" total. Color tags are
 * stripped before digit parsing so their hex codes never pollute a number.
 */
public final class GeHistoryParser {
	private static final int ROW_SCAN_LIMIT = 8;

	private GeHistoryParser() {
	}

	public static List<GeHistoryRow> parse(Widget container) {
		List<GeHistoryRow> rows = new ArrayList<>();
		if (container == null) {
			return rows;
		}
		Widget[] children = container.getDynamicChildren();
		if (children == null) {
			return rows;
		}
		for (int index = 0; index < children.length; index++) {
			String side = side(children[index]);
			if (side == null) {
				continue;
			}
			GeHistoryRow row = parseRow(children, index, side);
			if (row != null) {
				rows.add(row);
			}
		}
		return rows;
	}

	private static String side(Widget widget) {
		String text = plainText(widget);
		if (text == null) {
			return null;
		}
		if (text.startsWith("Bought")) {
			return "buy";
		}
		if (text.startsWith("Sold")) {
			return "sell";
		}
		return null;
	}

	private static GeHistoryRow parseRow(Widget[] children, int headerIndex, String side) {
		int itemId = -1;
		int quantity = 0;
		int priceEach = -1;
		long totalValue = -1;
		int end = Math.min(children.length, headerIndex + 1 + ROW_SCAN_LIMIT);
		// Stops at the next header so a sparse row never bleeds into the following one.
		for (int index = headerIndex + 1; index < end; index++) {
			Widget child = children[index];
			if (child == null) {
				continue;
			}
			if (side(child) != null) {
				break;
			}
			if (itemId <= 0 && child.getItemId() > 0) {
				itemId = child.getItemId();
				quantity = Math.max(1, child.getItemQuantity());
				continue;
			}
			String text = plainText(child);
			if (text == null) {
				continue;
			}
			if (text.contains("each")) {
				priceEach = parseEach(text);
			} else if (totalValue < 0 && text.contains("coins")) {
				totalValue = digits(truncateAtParen(text));
			}
		}
		if (itemId <= 0 || quantity <= 0) {
			return null;
		}
		if (priceEach <= 0 && totalValue > 0) {
			priceEach = (int) (totalValue / quantity);
		}
		if (priceEach <= 0) {
			return null;
		}
		return GeHistoryRow.builder()
				.itemId(itemId)
				.side(side)
				.quantity(quantity)
				.priceEach(priceEach)
				.build();
	}

	private static int parseEach(String text) {
		int separator = text.lastIndexOf('=');
		if (separator < 0) {
			return -1;
		}
		long value = digits(text.substring(separator + 1));
		return value > 0 && value <= Integer.MAX_VALUE ? (int) value : -1;
	}

	private static String truncateAtParen(String text) {
		int paren = text.indexOf('(');
		return paren < 0 ? text : text.substring(0, paren);
	}

	private static long digits(String text) {
		String cleaned = text.replaceAll("\\D", "");
		if (cleaned.isEmpty() || cleaned.length() > 15) {
			return -1;
		}
		return Long.parseLong(cleaned);
	}

	private static String plainText(Widget widget) {
		String text = widget != null ? widget.getText() : null;
		if (text == null || text.isEmpty()) {
			return null;
		}
		return text.replaceAll("<[^>]*>", " ").trim();
	}
}
