package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeTradeEvent {
	@SerializedName("account_hash")
	String accountHash;

	@SerializedName("idempotency_key")
	String idempotencyKey;

	@SerializedName("item_id")
	int itemId;

	String side;

	int quantity;

	@SerializedName("price_each")
	int priceEach;

	Integer slot;

	@SerializedName("occurred_at")
	String occurredAt;
}
