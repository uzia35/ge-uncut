package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeOffer {
	private final int slot;

	@SerializedName("item_id")
	private final int itemId;

	private final String side;

	private final String state;

	@SerializedName("quantity_filled")
	private final int quantityFilled;

	@SerializedName("quantity_total")
	private final int quantityTotal;

	@SerializedName("price_each")
	private final int priceEach;

	@SerializedName("occurred_at")
	private final String occurredAt;
}
