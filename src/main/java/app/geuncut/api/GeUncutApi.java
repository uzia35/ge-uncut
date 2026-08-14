package app.geuncut.api;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.ItemPrice;
import app.geuncut.dto.LinkSession;
import app.geuncut.dto.Movers;
import app.geuncut.dto.OfferPlacement;
import app.geuncut.dto.PositionsResponse;
import app.geuncut.dto.ScanRequest;

public interface GeUncutApi {
	void fetchFlips(ScanRequest request, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);

	void fetchItemPrice(int itemId, Consumer<ItemPrice> onSuccess, Consumer<ApiFailure> onError);

	void fetchPositions(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);

	void archivePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError);

	void fetchArchived(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);

	void restorePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError);

	void trackPair(long sellEventId, Runnable onSuccess, Consumer<ApiFailure> onError);

	void fetchOfferPlacements(Consumer<List<OfferPlacement>> onSuccess, Consumer<ApiFailure> onError);

	void fetchMovers(Consumer<Movers> onSuccess, Consumer<ApiFailure> onError);

	void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError);

	void postOffers(String accountHash, List<GeOffer> offers, String syncedAt, Runnable onSuccess, Consumer<ApiFailure> onError);

	void postGeHistory(String accountHash, List<GeHistoryRow> rows, Runnable onSuccess, Consumer<ApiFailure> onError);

	void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError);

	void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError);

	void unlinkAccount(String token, Runnable onSuccess, Consumer<ApiFailure> onError);
}
