package app.geuncut.api;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;

/**
 * The plugin's contract with geuncut.app. Services depend on this seam, never
 * on the HTTP implementation, so they are testable without a network.
 *
 * Authentication is entirely the transport's concern: callers never see the
 * token, and an expired or missing link surfaces as an unauthorized
 * ApiFailure.
 */
public interface GeUncutApi {
	void fetchFlips(String scanType, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);

	void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError);

	void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError);

	void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError);
}
