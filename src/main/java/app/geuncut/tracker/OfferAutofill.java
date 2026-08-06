package app.geuncut.tracker;

import java.util.ArrayList;
import java.util.List;

import app.geuncut.dto.Flip;
import app.geuncut.dto.Position;
import lombok.Value;

public final class OfferAutofill {
	private OfferAutofill() {
	}

	public enum Prompt {
		PRICE, QUANTITY
	}

	@Value
	public static class Choice {
		String label;
		long value;
		boolean base;
	}

	public static Prompt promptKind(String chatboxTitle) {
		if (chatboxTitle == null) {
			return null;
		}
		String title = chatboxTitle.trim();
		if (title.equals("Set a price for each item:")) {
			return Prompt.PRICE;
		}
		if (title.equals("How many do you wish to buy?") || title.equals("How many do you wish to sell?")) {
			return Prompt.QUANTITY;
		}
		return null;
	}

	public static Long resolve(int itemId, boolean sell, Prompt prompt,
			List<Flip> flips, List<Position> positions, String accountHash) {
		if (prompt == null) {
			return null;
		}
		Long value = sell ? forSell(itemId, prompt, positions, accountHash) : forBuy(itemId, prompt, flips);
		return value != null && value > 0 ? value : null;
	}

	public static List<Choice> choices(Long resolved, Prompt prompt, int percent) {
		return choices(resolved, prompt, percent, false);
	}

	public static List<Choice> choices(Long resolved, Prompt prompt, int percent, boolean compact) {
		if (resolved == null || resolved <= 0) {
			return List.of();
		}
		long base = resolved;
		if (prompt != Prompt.PRICE || percent <= 0) {
			return List.of(new Choice(format(base, compact), base, true));
		}
		List<Choice> choices = new ArrayList<>(3);
		long low = scale(base, 100 - percent);
		long high = scale(base, 100 + percent);
		choices.add(new Choice("-" + percent + "% " + format(low, compact), low, false));
		choices.add(new Choice(format(base, compact), base, true));
		choices.add(new Choice("+" + percent + "% " + format(high, compact), high, false));
		return choices;
	}

	private static long scale(long base, int percentOfBase) {
		return Math.max(1, Math.round(base * percentOfBase / 100.0));
	}

	private static String format(long value, boolean compact) {
		if (!compact || value < 100_000L) {
			return String.format("%,d", value);
		}
		if (value >= 1_000_000_000L) {
			return trim(value / 1_000_000_000.0) + "B";
		}
		if (value >= 1_000_000L) {
			return trim(value / 1_000_000.0) + "M";
		}
		return trim(value / 1_000.0) + "K";
	}

	private static String trim(double scaled) {
		String text = String.format("%.2f", scaled);
		if (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".0")) {
			text = text.substring(0, text.length() - 2);
		}
		return text.endsWith("0") && text.contains(".") ? text.substring(0, text.length() - 1) : text;
	}

	private static Long forBuy(int itemId, Prompt prompt, List<Flip> flips) {
		if (flips == null) {
			return null;
		}
		for (Flip flip : flips) {
			if (flip.getItemId() == itemId) {
				return prompt == Prompt.PRICE ? flip.getBuyPrice() : flip.getQuantity();
			}
		}
		return null;
	}

	private static Long forSell(int itemId, Prompt prompt, List<Position> positions, String accountHash) {
		if (positions == null) {
			return null;
		}
		for (Position position : positions) {
			if (position.getItemId() != itemId) {
				continue;
			}
			if (position.getAccountHash() != null && accountHash != null
					&& !position.getAccountHash().equals(accountHash)) {
				continue;
			}
			if (prompt == Prompt.PRICE) {
				return position.getTargetSellPrice();
			}
			long sold = position.getSoldQty() != null ? position.getSoldQty() : 0;
			return position.getQuantity() - sold;
		}
		return null;
	}
}
