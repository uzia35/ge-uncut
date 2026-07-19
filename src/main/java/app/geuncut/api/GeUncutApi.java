package app.geuncut.api;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import app.geuncut.dto.Movers;
import app.geuncut.dto.OfferPlacement;
import app.geuncut.dto.PositionsResponse;

/**
 * The plugin's contract with geuncut.app. Services depend on this seam, never
 * on the HTTP implementation, so they are testable without a network.
 *
 * Authentication is entirely the transport's concern: callers never see the
 * token, and an expired or missing link surfaces as an unauthorized
 * ApiFailure.
 */
public interface GeUncutApi {
	void fetchFlips(String scanType, String risk, Long capital, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);

	void fetchPositions(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);

	void archivePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError);

	void fetchArchived(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);

	void restorePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError);

	// Durable per-slot placement times, for seeding working-offer ages across
	// client restarts.
	void fetchOfferPlacements(Consumer<List<OfferPlacement>> onSuccess, Consumer<ApiFailure> onError);

	void fetchMovers(Consumer<Movers> onSuccess, Consumer<ApiFailure> onError);

	void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError);

	void postOffers(String accountHash, List<GeOffer> offers, String syncedAt, Runnable onSuccess, Consumer<ApiFailure> onError);

	void postGeHistory(String accountHash, List<GeHistoryRow> rows, Runnable onSuccess, Consumer<ApiFailure> onError);

	void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError);

	void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError);

	// Token passed explicitly: the caller clears config first, so the interceptor can no longer add it.
	void unlinkAccount(String token, Runnable onSuccess, Consumer<ApiFailure> onError);
}
