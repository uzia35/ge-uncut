package app.geuncut.service;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import app.geuncut.dto.Movers;
import app.geuncut.dto.PositionsResponse;

/**
 * Scriptable in-memory GeUncutApi. Calls are recorded; outcomes are whatever
 * the test arms before acting.
 */
class MockGeUncutApi implements GeUncutApi {
	boolean failNextPost;
	boolean failNextOffers;
	boolean deferNextPost;
	boolean deferNextPoll;
	String linkToken;
	String unlinkedToken;
	boolean linkPending = true;
	ApiFailure failure = ApiFailure.network("boom");
	LinkSession session;
	FlipsResponse flipsResponse;
	PositionsResponse positionsResponse;
	Movers moversResponse;

	final List<List<GeTradeEvent>> postedBatches = new ArrayList<>();
	final List<List<GeOffer>> postedOffers = new ArrayList<>();
	final List<List<GeHistoryRow>> postedHistory = new ArrayList<>();
	final List<Long> archivedPositions = new ArrayList<>();
	final List<Long> restoredPositions = new ArrayList<>();
	boolean failNextArchive;
	PositionsResponse archivedResponse;
	String lastHistoryAccountHash;
	String lastOffersAccountHash;
	String lastOffersSyncedAt;
	int pollCount;

	private Runnable pendingPostFailure;
	private Runnable pendingPoll;

	@Override
	public void fetchFlips(String scanType, String risk, Long capital, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError) {
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
	public void archivePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError) {
		if (failNextArchive) {
			failNextArchive = false;
			onError.accept(failure);
			return;
		}
		archivedPositions.add(positionId);
		onSuccess.run();
	}

	@Override
	public void fetchArchived(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError) {
		if (archivedResponse != null) {
			onSuccess.accept(archivedResponse);
		} else {
			onError.accept(failure);
		}
	}

	@Override
	public void restorePosition(long positionId, Runnable onSuccess, Consumer<ApiFailure> onError) {
		restoredPositions.add(positionId);
		onSuccess.run();
	}

	@Override
	public void fetchMovers(Consumer<Movers> onSuccess, Consumer<ApiFailure> onError) {
		if (moversResponse != null) {
			onSuccess.accept(moversResponse);
		} else {
			onError.accept(failure);
		}
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError) {
		if (deferNextPost) {
			deferNextPost = false;
			pendingPostFailure = () -> onError.accept(failure);
			return;
		}
		if (failNextPost) {
			failNextPost = false;
			onError.accept(failure);
			return;
		}
		postedBatches.add(new ArrayList<>(events));
		onSuccess.run();
	}

	// Fire a post outcome that postGeEvents held back (deferNextPost), so a test can
	// let accept() calls interleave between a flush's clear and its failure callback.
	void firePendingPostFailure() {
		Runnable held = pendingPostFailure;
		pendingPostFailure = null;
		if (held != null) {
			held.run();
		}
	}

	@Override
	public void postOffers(String accountHash, List<GeOffer> offers, String syncedAt, Runnable onSuccess, Consumer<ApiFailure> onError) {
		if (failNextOffers) {
			failNextOffers = false;
			onError.accept(failure);
			return;
		}
		lastOffersAccountHash = accountHash;
		lastOffersSyncedAt = syncedAt;
		postedOffers.add(new ArrayList<>(offers));
		onSuccess.run();
	}

	@Override
	public void postGeHistory(String accountHash, List<GeHistoryRow> rows, Runnable onSuccess, Consumer<ApiFailure> onError) {
		lastHistoryAccountHash = accountHash;
		postedHistory.add(new ArrayList<>(rows));
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
		if (deferNextPoll) {
			deferNextPoll = false;
			pendingPoll = () -> deliverPoll(onToken, onPending, onError);
			return;
		}
		deliverPoll(onToken, onPending, onError);
	}

	// Fire a poll outcome that pollLink held back (deferNextPoll), so a test can cancel
	// and restart a session before the stale poll's response lands.
	void firePendingPoll() {
		Runnable held = pendingPoll;
		pendingPoll = null;
		if (held != null) {
			held.run();
		}
	}

	@Override
	public void unlinkAccount(String token, Runnable onSuccess, Consumer<ApiFailure> onError) {
		unlinkedToken = token;
		onSuccess.run();
	}

	private void deliverPoll(Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError) {
		if (linkToken != null) {
			onToken.accept(linkToken);
		} else if (linkPending) {
			onPending.run();
		} else {
			onError.accept(failure);
		}
	}
}
