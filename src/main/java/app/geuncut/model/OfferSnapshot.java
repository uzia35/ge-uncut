package app.geuncut.model;

import lombok.Value;
import net.runelite.api.GrandExchangeOfferState;

@Value
public class OfferSnapshot {
	private final int itemId;
	private final int quantitySold;
	private final long spent;
	private final GrandExchangeOfferState state;
	private final int quantityTotal;
	private final long price;
	private final String offerId;
}
