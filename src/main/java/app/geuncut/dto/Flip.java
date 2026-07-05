package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Flip {
	@SerializedName("item_id")
	int itemId;

	String name;

	@SerializedName("buy_price")
	long buyPrice;

	@SerializedName("target_sell_price")
	long targetSellPrice;

	long quantity;

	@SerializedName("total_profit")
	long totalProfit;

	@SerializedName("roi_per_day")
	double roiPerDay;

	String strategy;
}
