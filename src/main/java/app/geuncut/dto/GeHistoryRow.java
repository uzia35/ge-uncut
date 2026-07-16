package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeHistoryRow {
	@SerializedName("item_id")
	private final int itemId;

	private final String side;

	private final int quantity;

	@SerializedName("price_each")
	private final int priceEach;
}
