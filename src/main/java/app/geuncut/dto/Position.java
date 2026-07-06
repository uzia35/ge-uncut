package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

/**
 * One open flip with its live evaluation. unrealizedProfit and roi are null
 * when the item's price is stale, so no P&L can be derived.
 */
@Value
@Builder
public class Position {
	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("item_name")
	private final String name;

	private final long quantity;

	@SerializedName("buy_price")
	private final long buyPrice;

	@SerializedName("unrealized_profit")
	private final Long unrealizedProfit;

	private final Double roi;

	private final String phase;

	@SerializedName("sell_reason")
	private final String sellReason;

	@SerializedName("price_stale")
	private final boolean priceStale;
}
