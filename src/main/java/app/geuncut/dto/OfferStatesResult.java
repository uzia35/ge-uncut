package app.geuncut.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OfferStatesResult {
	private final int fills;
}
