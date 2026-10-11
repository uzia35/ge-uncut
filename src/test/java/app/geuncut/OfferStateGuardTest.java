package app.geuncut;

import java.time.Instant;
import java.util.List;

import app.geuncut.dto.OfferState;
import app.geuncut.tracker.BuyLimitTracker;
import app.geuncut.tracker.OfferStateRecorder;
import app.geuncut.tracker.impl.InMemoryOfferLog;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferStateGuardTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int filled) {
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		when(offer.getItemId()).thenReturn(state == GrandExchangeOfferState.EMPTY ? 0 : 20997);
		when(offer.getPrice()).thenReturn(state == GrandExchangeOfferState.EMPTY ? 0L : 1000L);
		when(offer.getTotalQuantity()).thenReturn(state == GrandExchangeOfferState.EMPTY ? 0 : 10);
		when(offer.getQuantitySold()).thenReturn(filled);
		when(offer.getSpent()).thenReturn(filled * 1000L);
		return offer;
	}

	private static void deliver(OfferStateRecorder recorder, GameState gameState, int slot, GrandExchangeOffer offer) {
		if (GeUncutPlugin.shouldRecord(offer.getState(), gameState)) {
			recorder.record(OfferStateRecorder.stateOf(slot, offer, T0));
		}
	}

	@Test
	public void anEmptySlotIsOnlyRecordedWhileLoggedIn() {
		assertFalse(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.EMPTY, GameState.LOGGING_IN));
		assertFalse(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.EMPTY, GameState.LOGIN_SCREEN));
		assertFalse(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.EMPTY, GameState.HOPPING));
		assertTrue(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.EMPTY, GameState.LOGGED_IN));
		assertTrue(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.BUYING, GameState.LOGGING_IN));
		assertTrue(GeUncutPlugin.shouldRecord(GrandExchangeOfferState.SOLD, GameState.LOADING));
	}

	@Test
	public void aLoginAndHopSequenceRecordsOnlyTheGamesRealStates() {
		InMemoryOfferLog log = new InMemoryOfferLog();
		OfferStateRecorder recorder = new OfferStateRecorder(log, mock(BuyLimitTracker.class));

		recorder.reset();
		deliver(recorder, GameState.LOGGING_IN, 0, offer(GrandExchangeOfferState.EMPTY, 0));
		deliver(recorder, GameState.LOGGING_IN, 1, offer(GrandExchangeOfferState.EMPTY, 0));
		deliver(recorder, GameState.LOGGING_IN, 0, offer(GrandExchangeOfferState.BUYING, 3));
		recorder.onLoggedIn(4242L);
		deliver(recorder, GameState.LOGGED_IN, 0, offer(GrandExchangeOfferState.BUYING, 3));

		recorder.reset();
		deliver(recorder, GameState.HOPPING, 0, offer(GrandExchangeOfferState.EMPTY, 0));
		deliver(recorder, GameState.LOADING, 0, offer(GrandExchangeOfferState.BUYING, 5));
		recorder.onLoggedIn(4242L);
		deliver(recorder, GameState.LOGGED_IN, 1, offer(GrandExchangeOfferState.EMPTY, 0));

		List<OfferState> entries = log.read("4242", 0, Integer.MAX_VALUE).getEntries();
		assertEquals(3, entries.size());
		assertEquals("buying", entries.get(0).getState());
		assertEquals(3, entries.get(0).getQuantityFilled());
		assertEquals("buying", entries.get(1).getState());
		assertEquals(5, entries.get(1).getQuantityFilled());
		assertEquals("empty", entries.get(2).getState());
		assertEquals(1, entries.get(2).getSlot());
	}
}
