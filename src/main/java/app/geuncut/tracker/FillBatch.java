package app.geuncut.tracker;

import java.util.List;

import app.geuncut.dto.GeTradeEvent;
import lombok.Value;

@Value
public class FillBatch {
	private final List<GeTradeEvent> entries;
	private final long nextOffset;
}
