package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Flip {
	@SerializedName("item_id")
	private final int itemId;

	private final String name;

	@SerializedName("buy_price")
	private final long buyPrice;

	@SerializedName("target_sell_price")
	private final long targetSellPrice;

	private final long quantity;

	@SerializedName("total_profit")
	private final long totalProfit;

	@SerializedName("roi_per_day")
	private final double roiPerDay;

	private final String strategy;
}
