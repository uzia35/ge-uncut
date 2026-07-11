package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class MoverEntry {
	@SerializedName("item_id")
	private final int itemId;

	private final String name;

	@SerializedName("change_pct")
	private final double changePct;
}
