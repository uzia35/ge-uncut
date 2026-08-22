package app.geuncut.tracker;

import java.time.Instant;
import java.util.function.Consumer;

import app.geuncut.model.OfferDelta;
import net.runelite.api.GrandExchangeOfferState;

public interface OfferTracker {
	void onOfferChanged(
			int slot,
			int itemId,
			GrandExchangeOfferState state,
			int quantitySold,
			int spent,
			int totalQuantity,
			int price,
			Instant now,
			Consumer<OfferDelta> onFill);

	void loadFor(String accountHash);

	void reset();
}
