package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeTradeEvent {
	@SerializedName("account_hash")
	private final String accountHash;

	@SerializedName("idempotency_key")
	private final String idempotencyKey;

	@SerializedName("item_id")
	private final int itemId;

	private final String side;

	private final int quantity;

	@SerializedName("price_each")
	private final int priceEach;

	private final Integer slot;

	@SerializedName("occurred_at")
	private final String occurredAt;
}
