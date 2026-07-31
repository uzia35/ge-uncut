package app.geuncut.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NewsSignal {
	private final String direction;
	private final Integer confidence;
}
