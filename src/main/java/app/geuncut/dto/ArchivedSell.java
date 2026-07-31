package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ArchivedSell {
	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("item_name")
	private final String itemName;

	private final long quantity;

	@SerializedName("price_each")
	private final Long priceEach;

	@SerializedName("occurred_at")
	private final String occurredAt;
}
