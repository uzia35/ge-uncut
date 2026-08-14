package app.geuncut.tracker;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import app.geuncut.dto.ItemPrice;

public final class PriceHint {
	private PriceHint() {
	}

	public static long sidePrice(ItemPrice price, boolean sell) {
		if (price == null) {
			return 0;
		}
		Long value = sell ? price.getSellPrice() : price.getBuyPrice();
		return value != null && value > 0 ? value : 0;
	}

	public static boolean hasPrices(ItemPrice price) {
		return price != null
				&& price.getBuyPrice() != null && price.getBuyPrice() > 0
				&& price.getSellPrice() != null && price.getSellPrice() > 0;
	}

	public static Instant parse(String iso) {
		if (iso == null || iso.isEmpty()) {
			return null;
		}
		String text = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
		try {
			return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
		} catch (RuntimeException naive) {
			try {
				return Instant.parse(iso);
			} catch (RuntimeException other) {
				return null;
			}
		}
	}

	public static String ago(String iso, Instant now) {
		Instant then = parse(iso);
		if (then == null || now == null) {
			return "?";
		}
		long seconds = Duration.between(then, now).getSeconds();
		if (seconds < 0) {
			seconds = 0;
		}
		if (seconds < 60) {
			return "now";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}
}
