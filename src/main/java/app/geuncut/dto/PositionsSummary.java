package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

/** Portfolio totals for the panel's stats strip. */
@Value
@Builder
public class PositionsSummary {
	@SerializedName("open_unrealized")
	private final long openUnrealized;

	@SerializedName("today_realized")
	private final long todayRealized;

	@SerializedName("open_count")
	private final int openCount;
}
