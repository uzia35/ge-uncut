package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Position {
	private final long id;

	@SerializedName("item_id")
	private final int itemId;

	@SerializedName("item_name")
	private final String name;

	@SerializedName("account_hash")
	private final String accountHash;

	private final long quantity;

	@SerializedName("buy_price")
	private final long buyPrice;

	@SerializedName("unrealized_profit")
	private final Long unrealizedProfit;

	@SerializedName("sold_qty")
	private final Long soldQty;

	@SerializedName("sold_avg_price")
	private final Long soldAvgPrice;

	@SerializedName("realized_profit")
	private final Long realizedProfit;

	@SerializedName("tax_paid")
	private final Long taxPaid;

	@SerializedName("exit_price")
	private final Long exitPrice;

	@SerializedName("closed_at")
	private final String closedAt;

	@SerializedName("target_sell_price")
	private final Long targetSellPrice;

	@SerializedName("alert_price")
	private final Long alertPrice;

	@SerializedName("horizon_days")
	private final Integer horizonDays;

	@SerializedName("opened_at")
	private final String openedAt;

	private final Double roi;

	private final String phase;

	@SerializedName("sell_reason")
	private final String sellReason;

	@SerializedName("price_stale")
	private final boolean priceStale;
}
