package app.geuncut.service;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.impl.TradeSyncServiceImpl;
import app.geuncut.tracker.FillLog;
import app.geuncut.tracker.GeTax;
import app.geuncut.tracker.impl.InMemoryFillLog;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TradeSyncServiceTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");
	private static final int TBOW = 20997;

	private MockGeUncutApi api;
	private FillLog log;
	private TradeSyncService service;
	private Runnable flushTick;

	@Before
	public void setUp() {
		api = new MockGeUncutApi();
		log = new InMemoryFillLog();
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		service = new TradeSyncServiceImpl(api, log, executor);
		service.setInstallId("install-0f2c1a9b");
		flushTick = startAndCaptureTick(service, executor);
	}

	private static Runnable startAndCaptureTick(TradeSyncService svc, ScheduledExecutorService executor) {
		svc.start(() -> "acct-1");
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		return tick.getValue();
	}

	private TradeSyncService restarted(MockGeUncutApi freshApi, Runnable[] tickOut) {
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		TradeSyncService svc = new TradeSyncServiceImpl(freshApi, log, executor);
		svc.setInstallId("install-0f2c1a9b");
		tickOut[0] = startAndCaptureTick(svc, executor);
		return svc;
	}

	private static OfferDelta buy(String offerId, int quantity, int cumulative) {
		return new OfferDelta(TBOW, OfferDelta.Side.BUY, quantity, 1_000_000, 0, T0, offerId, cumulative);
	}

	private static OfferDelta buy(int quantity) {
		return buy("offer-" + quantity, quantity, quantity);
	}

	private long logged() {
		return log.read("acct-1", 0, Integer.MAX_VALUE).getEntries().size();
	}

	@Test
	public void acceptedFillsFlushAsOneBatch() {
		service.accept(buy(3));
		service.accept(buy(5));
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals(2, api.postedBatches.get(0).size());
		assertEquals("acct-1", api.postedBatches.get(0).get(0).getAccountHash());
		assertEquals("buy", api.postedBatches.get(0).get(0).getSide());
	}

	@Test
	public void emptyLogDoesNotPost() {
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void aSuccessfulFlushAdvancesTheCursorAndKeepsTheLog() {
		service.accept(buy(3));
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals(1, logged());
		assertEquals(1, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aTransientFailureLeavesTheCursorAndRetriesInOrder() {
		service.accept(buy(1));
		service.accept(buy(2));
		api.failNextPost = true;
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(0, log.deliveredOffset("acct-1"));
		assertEquals(2, logged());

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(2, api.postedBatches.get(0).get(1).getQuantity());
		assertEquals(2, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aBacklogBeyondTheServerCapDrainsInChunksInsteadOfWedging() {
		for (int i = 1; i <= 120; i++) {
			service.accept(buy(i));
		}
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(50, api.postedBatches.get(0).size());
		assertEquals(50, log.deliveredOffset("acct-1"));

		flushTick.run();
		flushTick.run();
		assertEquals(3, api.postedBatches.size());
		assertEquals(50, api.postedBatches.get(1).size());
		assertEquals(20, api.postedBatches.get(2).size());
		assertEquals(120, log.deliveredOffset("acct-1"));
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(51, api.postedBatches.get(1).get(0).getQuantity());
		assertEquals(101, api.postedBatches.get(2).get(0).getQuantity());
	}

	@Test
	public void anUnauthorizedFlushKeepsEveryFillAndTheCursor() {
		service.accept(buy(3));
		api.failNextPost = true;
		api.failure = ApiFailure.http(HttpURLConnection.HTTP_UNAUTHORIZED, "not linked");
		flushTick.run();

		assertTrue(api.postedBatches.isEmpty());
		assertEquals(1, logged());
		assertEquals(0, log.deliveredOffset("acct-1"));

		flushTick.run();
		assertTrue("an unauthorized client must go quiet, not poll every tick", api.postedBatches.isEmpty());
		assertEquals(1, logged());
	}

	@Test
	public void fillsObservedWhileUnlinkedAreLoggedAndSentAfterLinking() {
		service.setSendGate(() -> false);
		service.accept(buy(3));
		service.accept(buy(4));
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(2, logged());

		service.setSendGate(() -> true);
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(2, api.postedBatches.get(0).size());
		assertEquals(3, api.postedBatches.get(0).get(0).getQuantity());
	}

	@Test
	public void aFreshSessionReplaysTheWholeLogInOrderThenContinuesWithLiveFills() {
		service.accept(buy(1));
		service.accept(buy(2));
		flushTick.run();
		assertEquals(2, log.deliveredOffset("acct-1"));

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable[] tick = new Runnable[1];
		TradeSyncService session = restarted(next, tick);
		tick[0].run();

		assertEquals(1, next.postedBatches.size());
		assertEquals(2, next.postedBatches.get(0).size());
		assertEquals(1, next.postedBatches.get(0).get(0).getQuantity());
		assertEquals(2, next.postedBatches.get(0).get(1).getQuantity());
		assertEquals("the replay must not rewind the cursor", 2, log.deliveredOffset("acct-1"));

		session.accept(buy(3));
		tick[0].run();
		assertEquals(2, next.postedBatches.size());
		assertEquals(1, next.postedBatches.get(1).size());
		assertEquals(3, next.postedBatches.get(1).get(0).getQuantity());
		assertEquals(3, log.deliveredOffset("acct-1"));
	}

	@Test
	public void theReplayHappensOnceAndNotOnEveryTick() {
		service.accept(buy(1));
		flushTick.run();

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable[] tick = new Runnable[1];
		restarted(next, tick);
		tick[0].run();
		tick[0].run();
		tick[0].run();

		assertEquals(1, next.postedBatches.size());
	}

	@Test
	public void aLongLogReplaysInServerSizedChunksInOrder() {
		for (int i = 1; i <= 60; i++) {
			service.accept(buy(i));
		}
		flushTick.run();
		flushTick.run();
		assertEquals(60, log.deliveredOffset("acct-1"));

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable[] tick = new Runnable[1];
		restarted(next, tick);
		tick[0].run();
		tick[0].run();

		assertEquals(2, next.postedBatches.size());
		assertEquals(50, next.postedBatches.get(0).size());
		assertEquals(10, next.postedBatches.get(1).size());
		assertEquals(1, next.postedBatches.get(0).get(0).getQuantity());
		assertEquals(51, next.postedBatches.get(1).get(0).getQuantity());
		assertEquals(60, log.deliveredOffset("acct-1"));
	}

	@Test
	public void theSameFillObservedTwiceCarriesTheSameKey() {
		service.accept(buy("offer-a", 3, 3));
		service.accept(buy("offer-a", 3, 3));
		flushTick.run();

		List<GeTradeEvent> batch = api.postedBatches.get(0);
		assertEquals(batch.get(0).getIdempotencyKey(), batch.get(1).getIdempotencyKey());
	}

	@Test
	public void aReplacedOfferInTheSameSlotGetsADifferentKey() {
		service.accept(buy("offer-a", 3, 3));
		service.accept(buy("offer-b", 3, 3));
		flushTick.run();

		List<GeTradeEvent> batch = api.postedBatches.get(0);
		assertFalse(batch.get(0).getIdempotencyKey().equals(batch.get(1).getIdempotencyKey()));
	}

	@Test
	public void twoFillsOnTheSameOfferInTheSameTickGetDistinctKeys() {
		service.accept(buy("offer-a", 3, 3));
		service.accept(buy("offer-a", 3, 6));
		flushTick.run();

		List<GeTradeEvent> batch = api.postedBatches.get(0);
		assertEquals(2, batch.size());
		assertFalse(batch.get(0).getIdempotencyKey().equals(batch.get(1).getIdempotencyKey()));
	}

	@Test
	public void theKeyIsBuiltFromTheOfferAndItsRunningTotal() {
		service.accept(buy("offer-a", 3, 6));
		flushTick.run();

		assertEquals("acct-1:0:offer-a:6", api.postedBatches.get(0).get(0).getIdempotencyKey());
	}

	@Test
	public void theKeyStaysWithinTheServerLimit() {
		MockGeUncutApi worstApi = new MockGeUncutApi();
		ScheduledExecutorService worstExecutor = mock(ScheduledExecutorService.class);
		TradeSyncService worstService = new TradeSyncServiceImpl(worstApi, new InMemoryFillLog(), worstExecutor);
		worstService.start(() -> "-9223372036854775808");
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(worstExecutor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));

		worstService.accept(new OfferDelta(TBOW, OfferDelta.Side.BUY, 100_000_000, 2_000_000_000, 7, T0,
				java.util.UUID.randomUUID().toString(), 100_000_000));
		tick.getValue().run();

		assertTrue(worstApi.postedBatches.get(0).get(0).getIdempotencyKey().length() <= 128);
	}

	@Test
	public void everyEventCarriesTheWriteAheadLogProvenance() {
		service.accept(buy("offer-a", 3, 3));
		service.accept(buy("offer-a", 5, 8));
		flushTick.run();

		List<GeTradeEvent> batch = api.postedBatches.get(0);
		assertEquals(Long.valueOf(0), batch.get(0).getSeq());
		assertEquals(Long.valueOf(1), batch.get(1).getSeq());
		assertEquals("install-0f2c1a9b", batch.get(0).getInstallId());
		assertEquals("live", batch.get(0).getSource());
		assertEquals("offer-a", batch.get(0).getOfferInstanceId());
	}

	@Test
	public void everyEventCarriesItsOwnClientEventId() {
		service.accept(buy(3));
		service.accept(buy(5));
		flushTick.run();

		String first = api.postedBatches.get(0).get(0).getClientEventId();
		String second = api.postedBatches.get(0).get(1).getClientEventId();
		assertEquals(36, java.util.UUID.fromString(first).toString().length());
		assertFalse(first.equals(second));
	}

	@Test
	public void publishedAtIsRestampedOnRetryWhileTheKeySurvives() {
		service.accept(buy(3));
		api.failNextPost = true;
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		GeTradeEvent event = api.postedBatches.get(0).get(0);
		assertEquals("acct-1:0:offer-3:3", event.getIdempotencyKey());
		assertTrue(event.getPublishedAt() != null && !event.getPublishedAt().isEmpty());
		assertTrue(Instant.parse(event.getPublishedAt()).isAfter(T0));
	}

	private static GeHistoryRow row(int itemId, String side, int quantity, int priceEach) {
		return GeHistoryRow.builder().itemId(itemId).side(side).quantity(quantity).priceEach(priceEach).build();
	}

	@Test
	public void anUnseenHistoryRowIsImportedOnceAndNeverAgain() {
		List<GeHistoryRow> rows = Arrays.asList(row(TBOW, "buy", 4, 1_000_000));

		assertEquals(1, service.importHistory(rows));
		assertEquals(1, logged());
		assertEquals(0, service.importHistory(rows));
		assertEquals(1, logged());
	}

	@Test
	public void aHistoryRowAlreadyInTheLogIsNotImported() {
		service.accept(buy("offer-a", 4, 4));
		assertEquals(0, service.importHistory(Arrays.asList(row(TBOW, "buy", 4, 1_000_000))));
		assertEquals(1, logged());
	}

	@Test
	public void anImportedSellIsStoredAtItsPreTaxPriceAndFlaggedAsHistory() {
		int shown = GeTax.afterTax(21_000);
		service.importHistory(Arrays.asList(row(TBOW, "sell", 100, shown)));
		flushTick.run();

		GeTradeEvent event = api.postedBatches.get(0).get(0);
		assertEquals("sell", event.getSide());
		assertEquals("history", event.getSource());
		assertEquals(0, event.getSlot());
		assertEquals(100, event.getQuantity());
		assertTrue(Math.abs(event.getPriceEach() - 21_000) <= 1);
		assertNotNull(event.getOfferInstanceId());
		assertEquals("install-0f2c1a9b", event.getInstallId());
	}

	@Test
	public void anImportedOfferIsSeenByTheNextHistoryRead() {
		List<GeHistoryRow> rows = Arrays.asList(
				row(TBOW, "sell", 100, GeTax.afterTax(21_000)),
				row(TBOW, "buy", 4, 1_000_000));

		assertEquals(2, service.importHistory(rows));
		assertEquals(0, service.importHistory(rows));
		assertEquals(2, logged());
	}
}
