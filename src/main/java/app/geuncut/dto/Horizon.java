package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class Horizon {
	@SerializedName("recommended_days")
	private final int recommendedDays;
}
