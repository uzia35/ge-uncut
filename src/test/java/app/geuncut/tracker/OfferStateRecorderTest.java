package app.geuncut.tracker;

import java.time.Instant;
import java.util.List;

import app.geuncut.dto.OfferState;
import app.geuncut.tracker.impl.InMemoryOfferLog;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class OfferStateRecorderTest {
	private static final long ACCOUNT = 4242L;
	private static final String ACCT = "4242";
	private static final int TBOW = 20997;
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");

	private InMemoryOfferLog log;
	private BuyLimitTracker buyLimits;
	private OfferStateRecorder recorder;

	@Before
	public void setUp() {
		log = new InMemoryOfferLog();
		buyLimits = mock(BuyLimitTracker.class);
		recorder = new OfferStateRecorder(log, buyLimits);
		recorder.setInstallId("install-1");
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state, int itemId, long price, int total,
			int filled, long spent) {
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		when(offer.getItemId()).thenReturn(itemId);
		when(offer.getPrice()).thenReturn(price);
		when(offer.getTotalQuantity()).thenReturn(total);
		when(offer.getQuantitySold()).thenReturn(filled);
		when(offer.getSpent()).thenReturn(spent);
		return offer;
	}

	private static OfferState state(int slot, GrandExchangeOfferState state, int itemId, long price, int total,
			int filled, long spent, Instant at) {
		return OfferStateRecorder.stateOf(slot, offer(state, itemId, price, total, filled, spent), at);
	}

	private static OfferState buying(int slot, int filled) {
		return state(slot, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, filled, filled * 1000L, T0);
	}

	private List<OfferState> logged(String account) {
		return log.read(account, 0, Integer.MAX_VALUE).getEntries();
	}

	@Test
	public void everyGameStateMapsToItsNameAndSide() {
		Object[][] cases = {
				{ GrandExchangeOfferState.BUYING, "buying", "buy" },
				{ GrandExchangeOfferState.BOUGHT, "bought", "buy" },
				{ GrandExchangeOfferState.CANCELLED_BUY, "cancelled_buy", "buy" },
				{ GrandExchangeOfferState.SELLING, "selling", "sell" },
				{ GrandExchangeOfferState.SOLD, "sold", "sell" },
				{ GrandExchangeOfferState.CANCELLED_SELL, "cancelled_sell", "sell" },
		};
		for (Object[] c : cases) {
			OfferState mapped = state(1, (GrandExchangeOfferState) c[0], TBOW, 1000, 10, 4, 4000, T0);
			assertEquals(c[1], mapped.getState());
			assertEquals(c[2], mapped.getSide());
		}
	}

	@Test
	public void anEmptySlotIsSentWithNoOfferData() {
		OfferState empty = state(5, GrandExchangeOfferState.EMPTY, TBOW, 1000, 10, 4, 4000, T0);
		assertEquals("empty", empty.getState());
		assertEquals(5, empty.getSlot());
		assertEquals(0, empty.getItemId());
		assertNull(empty.getSide());
		assertEquals(0, empty.getPriceEach());
		assertEquals(0, empty.getQuantityTotal());
		assertEquals(0, empty.getQuantityFilled());
		assertEquals(0, empty.getSpent());
		assertEquals("2026-07-05T12:00:00Z", empty.getObservedAt());
	}

	@Test
	public void statesAreRecordedVerbatimWithSpentAndFilled() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(state(2, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 3, 2950, T0));

		List<OfferState> entries = logged(ACCT);
		assertEquals(1, entries.size());
		OfferState recorded = entries.get(0);
		assertEquals(Long.valueOf(0), recorded.getSeq());
		assertEquals("install-1", recorded.getInstallId());
		assertEquals(2, recorded.getSlot());
		assertEquals("buying", recorded.getState());
		assertEquals(TBOW, recorded.getItemId());
		assertEquals("buy", recorded.getSide());
		assertEquals(1000, recorded.getPriceEach());
		assertEquals(10, recorded.getQuantityTotal());
		assertEquals(3, recorded.getQuantityFilled());
		assertEquals(2950, recorded.getSpent());
		assertEquals("2026-07-05T12:00:00Z", recorded.getObservedAt());
	}

	@Test
	public void anIdenticalConsecutiveStateIsSkippedButAChangedOneIsRecorded() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));
		recorder.record(state(0, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 3, 3000, T0.plusSeconds(5)));
		recorder.record(buying(0, 5));
		recorder.record(state(0, GrandExchangeOfferState.CANCELLED_BUY, TBOW, 1000, 10, 5, 5000, T0));

		List<OfferState> entries = logged(ACCT);
		assertEquals(3, entries.size());
		assertEquals(3, entries.get(0).getQuantityFilled());
		assertEquals(5, entries.get(1).getQuantityFilled());
		assertEquals("cancelled_buy", entries.get(2).getState());
	}

	@Test
	public void dedupeIsPerSlot() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));
		recorder.record(buying(1, 3));
		recorder.record(buying(0, 3));

		List<OfferState> entries = logged(ACCT);
		assertEquals(2, entries.size());
		assertEquals(0, entries.get(0).getSlot());
		assertEquals(1, entries.get(1).getSlot());
	}

	@Test
	public void aSpendChangeAloneIsRecorded() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));
		recorder.record(state(0, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 3, 2900, T0));

		assertEquals(2, logged(ACCT).size());
	}

	@Test
	public void anEmptySlotIsRecordedWhenLoggedIn() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(state(0, GrandExchangeOfferState.BOUGHT, TBOW, 1000, 10, 10, 10_000, T0));
		recorder.record(state(0, GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, 0, T0));

		List<OfferState> entries = logged(ACCT);
		assertEquals(2, entries.size());
		assertEquals("empty", entries.get(1).getState());
		assertEquals(0, entries.get(1).getItemId());
	}

	@Test
	public void statesBeforeLoginAreHeldAndReleasedInOrderToTheRightAccount() {
		recorder.reset();
		recorder.record(buying(0, 1));
		recorder.record(buying(1, 2));
		recorder.record(buying(2, 3));
		assertTrue(logged(ACCT).isEmpty());

		recorder.onLoggedIn(ACCOUNT);

		List<OfferState> entries = logged(ACCT);
		assertEquals(3, entries.size());
		assertEquals(0, entries.get(0).getSlot());
		assertEquals(1, entries.get(1).getSlot());
		assertEquals(2, entries.get(2).getSlot());
		assertEquals("install-1", entries.get(0).getInstallId());
		assertTrue(logged("-1").isEmpty());
	}

	@Test
	public void anUnknownAccountKeepsHolding() {
		recorder.record(buying(0, 1));
		recorder.onLoggedIn(-1);
		assertTrue(logged("-1").isEmpty());

		recorder.onLoggedIn(ACCOUNT);
		assertEquals(1, logged(ACCT).size());
	}

	@Test
	public void heldStatesAreDroppedOnReset() {
		recorder.record(buying(0, 1));
		recorder.reset();
		recorder.onLoggedIn(ACCOUNT);

		assertTrue(logged(ACCT).isEmpty());
	}

	@Test
	public void aLoginAndHopRecordOnlyWhatTheGameReports() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));
		recorder.record(buying(1, 0));

		recorder.reset();
		recorder.record(buying(0, 3));
		recorder.record(buying(1, 0));
		recorder.onLoggedIn(ACCOUNT);

		recorder.reset();
		recorder.record(buying(0, 4));
		recorder.onLoggedIn(ACCOUNT);

		List<OfferState> entries = logged(ACCT);
		assertEquals(5, entries.size());
		assertEquals(3, entries.get(0).getQuantityFilled());
		assertEquals(0, entries.get(1).getQuantityFilled());
		assertEquals(3, entries.get(2).getQuantityFilled());
		assertEquals(0, entries.get(3).getQuantityFilled());
		assertEquals(4, entries.get(4).getQuantityFilled());
		for (OfferState entry : entries) {
			assertEquals("buying", entry.getState());
		}
	}

	@Test
	public void switchingAccountsWritesToTheNewAccountsLog() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));
		recorder.reset();
		recorder.onLoggedIn(777L);
		recorder.record(buying(0, 3));

		assertEquals(1, logged(ACCT).size());
		assertEquals(1, logged("777").size());
	}

	@Test
	public void aRiseOnTheSameBuyFeedsTheBuyLimitTimer() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 0));
		recorder.record(state(0, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 4, 4000, T0.plusSeconds(30)));
		recorder.record(state(0, GrandExchangeOfferState.BOUGHT, TBOW, 1000, 10, 10, 10_000, T0.plusSeconds(60)));

		verify(buyLimits).recordBuy(TBOW, 4, T0.plusSeconds(30));
		verify(buyLimits).recordBuy(TBOW, 6, T0.plusSeconds(60));
		verifyNoMoreInteractions(buyLimits);
	}

	@Test
	public void theBuyLimitTimerIgnoresSellsNewOffersAndFirstSightings() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 5));
		recorder.record(state(1, GrandExchangeOfferState.SELLING, TBOW, 1000, 10, 0, 0, T0));
		recorder.record(state(1, GrandExchangeOfferState.SELLING, TBOW, 1000, 10, 4, 4000, T0));
		recorder.record(state(2, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 2, 2000, T0));
		recorder.record(state(2, GrandExchangeOfferState.BUYING, TBOW, 1100, 10, 5, 5500, T0));
		recorder.record(state(3, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 2, 2000, T0));
		recorder.record(state(3, GrandExchangeOfferState.BUYING, 561, 1000, 10, 5, 5000, T0));
		recorder.record(state(4, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 2, 2000, T0));
		recorder.record(state(4, GrandExchangeOfferState.BUYING, TBOW, 1000, 20, 5, 5000, T0));

		verify(buyLimits, never()).recordBuy(anyInt(), anyInt(), any(Instant.class));
	}

	@Test
	public void heldStatesFeedTheBuyLimitTimerOnRelease() {
		recorder.record(buying(0, 1));
		recorder.record(state(0, GrandExchangeOfferState.BUYING, TBOW, 1000, 10, 3, 3000, T0.plusSeconds(9)));
		recorder.onLoggedIn(ACCOUNT);

		verify(buyLimits).recordBuy(TBOW, 2, T0.plusSeconds(9));
	}

	@Test
	public void nothingIsRememberedAcrossRecorders() {
		recorder.onLoggedIn(ACCOUNT);
		recorder.record(buying(0, 3));

		OfferStateRecorder fresh = new OfferStateRecorder(log, buyLimits);
		fresh.onLoggedIn(ACCOUNT);
		fresh.record(buying(0, 3));

		assertEquals(2, logged(ACCT).size());
		assertNull(logged(ACCT).get(1).getInstallId());
	}
}
