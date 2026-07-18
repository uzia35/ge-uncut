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
	// Server position id, needed to archive ("not a flip") the exact flip.
	private final long id;

	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("item_name")
	private final String name;

	private final long quantity;

	@SerializedName("buy_price")
	private final long buyPrice;

	@SerializedName("unrealized_profit")
	private final Long unrealizedProfit;

	// Partial-fill state: how much has sold, at what average, and the exact
	// realized gp so far. Feeds the expanded card's Bought/Sold/Realized rows
	// (and History's "Sold at").
	@SerializedName("sold_qty")
	private final Long soldQty;

	@SerializedName("sold_avg_price")
	private final Long soldAvgPrice;

	@SerializedName("realized_profit")
	private final Long realizedProfit;

	private final Double roi;

	private final String phase;

	@SerializedName("sell_reason")
	private final String sellReason;

	@SerializedName("price_stale")
	private final boolean priceStale;
}
