package app.geuncut.model;

import lombok.Value;

@Value
public class OfferSnapshot {
	int itemId;
	int quantitySold;
	int spent;
	boolean selling;
}
