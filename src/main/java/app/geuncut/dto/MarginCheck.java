package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

/** A quarantined price-probe pair, listed in History and nowhere else. */
@Value
@Builder
public class MarginCheck {
	@SerializedName("sell_event_id")
	private final long sellEventId;

	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("item_name")
	private final String itemName;

	@SerializedName("buy_price")
	private final long buyPrice;

	@SerializedName("sell_price")
	private final long sellPrice;
}
