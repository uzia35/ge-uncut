package app.geuncut.service;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;

/**
 * Scriptable in-memory GeUncutApi. Calls are recorded; outcomes are whatever
 * the test arms before acting.
 */
class MockGeUncutApi implements GeUncutApi {
	boolean tokenPresent = true;
	boolean failNextPost;
	String linkToken;
	boolean linkPending = true;
	String error = "boom";
	LinkSession session;
	FlipsResponse flipsResponse;

	final List<List<GeTradeEvent>> postedBatches = new ArrayList<>();
	int pollCount;

	@Override
	public boolean hasToken() {
		return tokenPresent;
	}

	@Override
	public void fetchFlips(String scanType, Consumer<FlipsResponse> onSuccess, Consumer<String> onError) {
		if (flipsResponse != null) {
			onSuccess.accept(flipsResponse);
		} else {
			onError.accept(error);
		}
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<String> onError) {
		if (failNextPost) {
			failNextPost = false;
			onError.accept(error);
			return;
		}
		postedBatches.add(new ArrayList<>(events));
		onSuccess.run();
	}

	@Override
	public void startLink(Consumer<LinkSession> onSuccess, Consumer<String> onError) {
		if (session != null) {
			onSuccess.accept(session);
		} else {
			onError.accept(error);
		}
	}

	@Override
	public void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<String> onError) {
		pollCount++;
		if (linkToken != null) {
			onToken.accept(linkToken);
		} else if (linkPending) {
			onPending.run();
		} else {
			onError.accept(error);
		}
	}
}
