package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

/**
 * One GE slot's durable placement record from the server: when the offer the
 * slot currently holds was placed, with the identity fields needed to apply it
 * only to that offer. Survives client restarts, unlike the local slot state.
 */
@Value
@Builder
public class OfferPlacement {
	@SerializedName("account_hash")
	private final String accountHash;

	private final int slot;

	@SerializedName("item_id")
	private final int itemId;

	private final String side;

	@SerializedName("price_each")
	private final int priceEach;

	@SerializedName("quantity_total")
	private final int quantityTotal;

	@SerializedName("quantity_filled")
	private final int quantityFilled;

	@SerializedName("placed_at")
	private final String placedAt;
}
