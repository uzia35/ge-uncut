package app.geuncut.api;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;

/**
 * The plugin's contract with geuncut.app. Services depend on this seam, never
 * on the HTTP implementation, so they are testable without a network.
 */
public interface GeUncutApi {
	boolean hasToken();

	void fetchFlips(String scanType, Consumer<FlipsResponse> onSuccess, Consumer<String> onError);

	void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<String> onError);

	void startLink(Consumer<LinkSession> onSuccess, Consumer<String> onError);

	void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<String> onError);
}
