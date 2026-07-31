package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class Fill {
	@SerializedName("worst_case_hours")
	private final Double worstCaseHours;

	private final String speed;
}
