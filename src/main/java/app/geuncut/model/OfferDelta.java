package app.geuncut.model;

import java.time.Instant;

import lombok.Value;

/**
 * One observed fill: the change in quantity and gold between two states of the
 * same GE slot. Price is the average over the delta, not the offer price.
 */
@Value
public class OfferDelta {
	public enum Side {
		BUY,
		SELL
	}

	int itemId;
	Side side;
	int quantity;
	int priceEach;
	int slot;
	Instant occurredAt;
}
