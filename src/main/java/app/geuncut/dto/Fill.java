package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

// worstCaseHours is null when volume is too thin to time; speed is the fallback bucket.
@Value
public class Fill {
	@SerializedName("worst_case_hours")
	private final Double worstCaseHours;

	private final String speed;
}
