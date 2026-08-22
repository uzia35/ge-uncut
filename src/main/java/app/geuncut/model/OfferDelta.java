package app.geuncut.model;

import java.time.Instant;

import lombok.Value;

@Value
public class OfferDelta {
	public enum Side {
		BUY,
		SELL
	}

	private final int itemId;
	private final Side side;
	private final int quantity;
	private final int priceEach;
	private final int slot;
	private final Instant occurredAt;
	private final String offerId;
	private final int cumulativeQuantitySold;
}
