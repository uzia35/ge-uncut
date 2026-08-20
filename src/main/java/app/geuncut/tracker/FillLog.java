package app.geuncut.tracker;

import java.util.Collection;
import java.util.List;

import app.geuncut.dto.GeTradeEvent;

public interface FillLog {
	void append(String accountHash, GeTradeEvent event);

	List<GeTradeEvent> pending(String accountHash);

	void ack(String accountHash, Collection<String> idempotencyKeys);
}
