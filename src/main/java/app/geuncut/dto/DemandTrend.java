package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class DemandTrend {
	private final Direction direction;
	private final Double ratio;

	public enum Direction {
		@SerializedName("rising")
		RISING,
		@SerializedName("steady")
		STEADY,
		@SerializedName("falling")
		FALLING,
		@SerializedName("unknown")
		UNKNOWN
	}
}
