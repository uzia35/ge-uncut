package app.geuncut.tracker.impl;

import lombok.Value;

@Value
class OfferSnapshot {
	int itemId;
	int quantitySold;
	int spent;
	boolean selling;
}
