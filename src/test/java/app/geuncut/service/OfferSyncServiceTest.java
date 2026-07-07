package app.geuncut.service;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.geuncut.api.ApiFailure;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.GeOffer;
import app.geuncut.service.impl.OfferSyncServiceImpl;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OfferSyncServiceTest {
	private static final Instant T0 = Instant.parse("2026-07-06T03:00:00Z");

	private MockGeUncutApi api;
	private OfferSyncService service;
	private Runnable flushTick;
	private boolean syncEnabled = true;

	@Before
	public void setUp() {
		api = new MockGeUncutApi();
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		GeUncutConfig config = new GeUncutConfig() {
			@Override
			public boolean syncTrades() {
				return syncEnabled;
			}
		};
		service = new OfferSyncServiceImpl(api, config, executor);
		service.start(() -> "acct-1");

		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		flushTick = tick.getValue();
	}

	@Test
	public void recordedOfferFlushesAsSnapshot() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 40, 100, 128, T0);
		flushTick.run();

		assertEquals(1, api.postedOffers.size());
		assertEquals(1, api.postedOffers.get(0).size());
		assertEquals("acct-1", api.lastOffersAccountHash);
		GeOffer offer = api.postedOffers.get(0).get(0);
		assertEquals(0, offer.getSlot());
		assertEquals(561, offer.getItemId());
		assertEquals("buy", offer.getSide());
		assertEquals("buying", offer.getState());
		assertEquals(40, offer.getQuantityFilled());
		assertEquals(100, offer.getQuantityTotal());
		assertEquals(128, offer.getPriceEach());
		// A monotonic snapshot time is sent so the server can drop out-of-order posts.
		assertNotNull(api.lastOffersSyncedAt);
	}

	@Test
	public void allOccupiedSlotsFlushTogether() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		service.record(1, 556, GrandExchangeOfferState.SELLING, 5, 50, 4, T0);
		flushTick.run();

		assertEquals(1, api.postedOffers.size());
		assertEquals(2, api.postedOffers.get(0).size());
	}

	@Test
	public void emptyStateClearsSlotAndFlushesEmptySnapshot() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		flushTick.run();
		service.record(0, 561, GrandExchangeOfferState.EMPTY, 0, 100, 128, T0);
		flushTick.run();

		assertEquals(2, api.postedOffers.size());
		assertTrue(api.postedOffers.get(1).isEmpty());
	}

	@Test
	public void unchangedStateDoesNotReflush() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedOffers.size());
	}

	@Test
	public void emptyQueueDoesNotPost() {
		flushTick.run();
		assertTrue(api.postedOffers.isEmpty());
	}

	@Test
	public void failedFlushRetainsStateAndRetries() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		api.failNextOffers = true;
		flushTick.run();
		assertTrue(api.postedOffers.isEmpty());

		flushTick.run();
		assertEquals(1, api.postedOffers.size());
		assertEquals(1, api.postedOffers.get(0).size());
	}

	@Test
	public void unauthorizedFlushDropsWithoutRetry() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		api.failNextOffers = true;
		api.failure = ApiFailure.http(HttpURLConnection.HTTP_UNAUTHORIZED, "not linked");
		flushTick.run();

		flushTick.run();
		assertTrue(api.postedOffers.isEmpty());
	}

	@Test
	public void syncDisabledRecordsNothing() {
		syncEnabled = false;
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);
		flushTick.run();

		assertTrue(api.postedOffers.isEmpty());
		assertTrue(service.current().isEmpty());
	}

	@Test
	public void sellStateMapsSideAndState() {
		service.record(2, 561, GrandExchangeOfferState.CANCELLED_SELL, 10, 20, 130, T0);
		flushTick.run();

		GeOffer offer = api.postedOffers.get(0).get(0);
		assertEquals("sell", offer.getSide());
		assertEquals("cancelled_sell", offer.getState());
	}

	@Test
	public void malformedOfferIsDropped() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 0, 128, T0);
		flushTick.run();

		assertTrue(api.postedOffers.isEmpty());
		assertTrue(service.current().isEmpty());
	}

	@Test
	public void currentReturnsWorkingOffers() {
		service.record(0, 561, GrandExchangeOfferState.BUYING, 0, 100, 128, T0);

		assertEquals(1, service.current().size());
		assertEquals(561, service.current().get(0).getItemId());
	}
}
