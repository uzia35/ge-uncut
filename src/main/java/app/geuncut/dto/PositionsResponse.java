package app.geuncut.dto;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PositionsResponse {
	private final List<Position> positions;
	private final PositionsSummary summary;
}
