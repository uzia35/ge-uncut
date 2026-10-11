package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class OfferState {
	private final Long seq;

	@SerializedName("install_id")
	private final String installId;

	private final int slot;

	private final String state;

	@SerializedName("item_id")
	private final int itemId;

	private final String side;

	@SerializedName("price_each")
	private final long priceEach;

	@SerializedName("quantity_total")
	private final int quantityTotal;

	@SerializedName("quantity_filled")
	private final int quantityFilled;

	private final long spent;

	@SerializedName("observed_at")
	private final String observedAt;
}
