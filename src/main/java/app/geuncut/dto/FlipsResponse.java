package app.geuncut.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FlipsResponse {
	private final List<Flip> flips;

	@SerializedName("my_capital")
	private final Long myCapital;

	@SerializedName("my_min_profit")
	private final Long myMinProfit;

	@SerializedName("my_min_roi")
	private final Double myMinRoi;

	@SerializedName("min_profit_floor")
	private final Long minProfitFloor;

	@SerializedName("min_roi_floor")
	private final Double minRoiFloor;
}
