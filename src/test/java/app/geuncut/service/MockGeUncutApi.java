package app.geuncut.service;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import app.geuncut.dto.PositionsResponse;

/**
 * Scriptable in-memory GeUncutApi. Calls are recorded; outcomes are whatever
 * the test arms before acting.
 */
class MockGeUncutApi implements GeUncutApi {
	boolean failNextPost;
	boolean failNextOffers;
	String linkToken;
	boolean linkPending = true;
	ApiFailure failure = ApiFailure.network("boom");
	LinkSession session;
	FlipsResponse flipsResponse;
	PositionsResponse positionsResponse;

	final List<List<GeTradeEvent>> postedBatches = new ArrayList<>();
	final List<List<GeOffer>> postedOffers = new ArrayList<>();
	String lastOffersAccountHash;
	int pollCount;

	@Override
	public void fetchFlips(String scanType, String risk, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError) {
		if (flipsResponse != null) {
			onSuccess.accept(flipsResponse);
		} else {
			onError.accept(failure);
		}
	}

	@Override
	public void fetchPositions(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError) {
		if (positionsResponse != null) {
			onSuccess.accept(positionsResponse);
		} else {
			onError.accept(failure);
		}
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError) {
		if (failNextPost) {
			failNextPost = false;
			onError.accept(failure);
			return;
		}
		postedBatches.add(new ArrayList<>(events));
		onSuccess.run();
	}

	@Override
	public void postOffers(String accountHash, List<GeOffer> offers, Runnable onSuccess, Consumer<ApiFailure> onError) {
		if (failNextOffers) {
			failNextOffers = false;
			onError.accept(failure);
			return;
		}
		lastOffersAccountHash = accountHash;
		postedOffers.add(new ArrayList<>(offers));
		onSuccess.run();
	}

	@Override
	public void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError) {
		if (session != null) {
			onSuccess.accept(session);
		} else {
			onError.accept(failure);
		}
	}

	@Override
	public void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError) {
		pollCount++;
		if (linkToken != null) {
			onToken.accept(linkToken);
		} else if (linkPending) {
			onPending.run();
		} else {
			onError.accept(failure);
		}
	}
}
