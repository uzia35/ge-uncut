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

	@SerializedName("profit_per_item")
	private final long profitPerItem;

	@SerializedName("position_cost")
	private final long positionCost;

	private final double roi;

	@SerializedName("roi_per_day")
	private final double roiPerDay;

	@SerializedName("spread_pct")
	private final Double spreadPct;

	@SerializedName("entry_discount_pct")
	private final Double entryDiscountPct;

	private final Horizon horizon;

	@SerializedName("demand_trend")
	private final DemandTrend demandTrend;

	@SerializedName("buy_fill")
	private final Fill buyFill;

	@SerializedName("sell_fill")
	private final Fill sellFill;

	private final String strategy;
}
