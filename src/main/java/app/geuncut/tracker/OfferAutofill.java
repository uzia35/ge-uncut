package app.geuncut.tracker;

import java.util.List;

import app.geuncut.dto.Flip;
import app.geuncut.dto.Position;

/**
 * Resolves what to pre-type into the GE offer setup's number prompt.
 * Buy offers take the finder card's price and quantity; sell offers on a
 * tracked flip take its sell target and whatever is left to sell. Pure logic;
 * the plugin does the widget work.
 */
public final class OfferAutofill {
	private OfferAutofill() {
	}

	public enum Prompt {
		PRICE, QUANTITY
	}

	/** The GE setup's chatbox prompt titles; anything else is not ours to fill. */
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
			// An untagged position fills for any account; a tagged one only for its own.
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
