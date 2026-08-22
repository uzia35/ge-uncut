package app.geuncut.model;

import lombok.Value;
import net.runelite.api.GrandExchangeOfferState;

@Value
public class OfferSnapshot {
	private final int itemId;
	private final int quantitySold;
	private final int spent;
	private final GrandExchangeOfferState state;
	private final int quantityTotal;
	private final int price;
	private final String offerId;
}
