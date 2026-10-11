package app.geuncut.tracker;

import java.util.List;

import app.geuncut.dto.OfferState;
import lombok.Value;

@Value
public class OfferBatch {
	private final List<OfferState> entries;
	private final long nextOffset;
}
