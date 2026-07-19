package app.geuncut.dto;

import lombok.Builder;
import lombok.Value;

/** A live news signal on a flip's item; presence alone drives the tag. */
@Value
@Builder
public class NewsSignal {
	private final String direction;
	private final Integer confidence;
}
