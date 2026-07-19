package app.geuncut.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PositionsResponse {
	private final List<Position> positions;
	private final PositionsSummary summary;

	// History-tab extras (archived endpoint only): the same content the
	// website's History list shows beyond archived positions.
	@SerializedName("margin_checks")
	private final List<MarginCheck> marginChecks;

	@SerializedName("archived_sells")
	private final List<ArchivedSell> archivedSells;
}
