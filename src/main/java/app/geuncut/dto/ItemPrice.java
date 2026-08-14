package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class ItemPrice {
	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("buy_price")
	private final Long buyPrice;

	@SerializedName("sell_price")
	private final Long sellPrice;

	@SerializedName("low_time")
	private final String lowTime;

	@SerializedName("high_time")
	private final String highTime;
}
