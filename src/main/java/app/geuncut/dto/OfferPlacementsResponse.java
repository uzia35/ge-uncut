package app.geuncut.dto;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OfferPlacementsResponse {
	private final List<OfferPlacement> placements;
}
