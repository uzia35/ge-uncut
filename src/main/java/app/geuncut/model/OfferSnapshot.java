package app.geuncut.model;

import lombok.Value;

@Value
public class OfferSnapshot {
	private final int itemId;
	private final int quantitySold;
	private final int spent;
	private final boolean selling;
}
