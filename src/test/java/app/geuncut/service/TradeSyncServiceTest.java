package app.geuncut.service;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.impl.TradeSyncServiceImpl;
import app.geuncut.tracker.FillLog;
import app.geuncut.tracker.impl.InMemoryFillLog;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TradeSyncServiceTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");

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
		flushTick = startAndCaptureTick(service, executor);
	}

	private static Runnable startAndCaptureTick(TradeSyncService svc, ScheduledExecutorService executor) {
		svc.start(() -> "acct-1");
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		return tick.getValue();
	}

	private static OfferDelta buy(int quantity) {
		return new OfferDelta(20997, OfferDelta.Side.BUY, quantity, 1_000_000, 0, T0);
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
	public void aSuccessfulFlushAcksSoTheNextFlushIsANoOp() {
		service.accept(buy(3));
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertTrue(log.pending("acct-1").isEmpty());
	}

	@Test
	public void aTransientFailureLeavesFillsInTheLogAndRetriesInOrder() {
		service.accept(buy(1));
		service.accept(buy(2));
		api.failNextPost = true;
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(2, log.pending("acct-1").size());

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(2, api.postedBatches.get(0).get(1).getQuantity());
	}

	@Test
	public void aBacklogBeyondTheServerCapDrainsInChunksInsteadOfWedging() {
		for (int i = 1; i <= 120; i++) {
			service.accept(buy(i));
		}
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(50, api.postedBatches.get(0).size());
		assertEquals(70, log.pending("acct-1").size());

		flushTick.run();
		flushTick.run();
		assertEquals(3, api.postedBatches.size());
		assertEquals(50, api.postedBatches.get(1).size());
		assertEquals(20, api.postedBatches.get(2).size());
		assertTrue(log.pending("acct-1").isEmpty());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(51, api.postedBatches.get(1).get(0).getQuantity());
		assertEquals(101, api.postedBatches.get(2).get(0).getQuantity());
	}

	@Test
	public void aFailedChunkStaysPendingAndRetriesTheSameOldestFills() {
		for (int i = 1; i <= 60; i++) {
			service.accept(buy(i));
		}
		api.failNextPost = true;
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(60, log.pending("acct-1").size());

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(50, api.postedBatches.get(0).size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
	}

	@Test
	public void unauthorizedFlushDropsInsteadOfGrowingTheLogForever() {
		service.accept(buy(3));
		api.failNextPost = true;
		api.failure = ApiFailure.http(HttpURLConnection.HTTP_UNAUTHORIZED, "not linked");
		flushTick.run();

		assertTrue(api.postedBatches.isEmpty());
		assertTrue(log.pending("acct-1").isEmpty());
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void unsentFillsSurviveARestartAndAreReplayed() {
		service.accept(buy(3));
		service.accept(buy(4));

		MockGeUncutApi apiAfter = new MockGeUncutApi();
		ScheduledExecutorService executorAfter = mock(ScheduledExecutorService.class);
		TradeSyncService restarted = new TradeSyncServiceImpl(apiAfter, log, executorAfter);
		Runnable tickAfter = startAndCaptureTick(restarted, executorAfter);
		tickAfter.run();

		assertEquals(1, apiAfter.postedBatches.size());
		assertEquals(2, apiAfter.postedBatches.get(0).size());
		assertEquals(3, apiAfter.postedBatches.get(0).get(0).getQuantity());
		assertEquals(4, apiAfter.postedBatches.get(0).get(1).getQuantity());
	}

	@Test
	public void aReplayedFillCarriesTheSameStableKeyAsItsFirstSend() {
		service.accept(buy(3));
		api.failNextPost = true;
		flushTick.run();

		MockGeUncutApi apiAfter = new MockGeUncutApi();
		ScheduledExecutorService executorAfter = mock(ScheduledExecutorService.class);
		TradeSyncService restarted = new TradeSyncServiceImpl(apiAfter, log, executorAfter);
		startAndCaptureTick(restarted, executorAfter).run();

		GeTradeEvent event = apiAfter.postedBatches.get(0).get(0);
		assertEquals(event.getClientEventId(),
				event.getIdempotencyKey().substring(event.getIdempotencyKey().length() - 36));
	}

	@Test
	public void idempotencyKeyIsStableForTheSameFill() {
		service.accept(buy(3));
		flushTick.run();
		String key = api.postedBatches.get(0).get(0).getIdempotencyKey();
		assertTrue(key.contains("acct-1"));
		assertTrue(key.contains("20997"));
		assertTrue(key.contains(String.valueOf(T0.toEpochMilli())));
	}

	@Test
	public void identicalSameTickFillsBothPersistWithDistinctKeys() {
		service.accept(buy(3));
		service.accept(buy(3));
		assertEquals(2, log.pending("acct-1").size());
		flushTick.run();

		String first = api.postedBatches.get(0).get(0).getIdempotencyKey();
		String second = api.postedBatches.get(0).get(1).getIdempotencyKey();
		assertFalse(first.equals(second));
	}

	@Test
	public void idempotencyKeyStaysWithinTheServerLimit() {
		MockGeUncutApi worstApi = new MockGeUncutApi();
		ScheduledExecutorService worstExecutor = mock(ScheduledExecutorService.class);
		TradeSyncService worstService = new TradeSyncServiceImpl(worstApi, new InMemoryFillLog(), worstExecutor);
		worstService.start(() -> "-9223372036854775808");
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(worstExecutor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));

		worstService.accept(new OfferDelta(20997, OfferDelta.Side.BUY, 100_000_000, 2_000_000_000, 7, T0));
		tick.getValue().run();

		assertTrue(worstApi.postedBatches.get(0).get(0).getIdempotencyKey().length() <= 128);
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
	public void publishedAtIsRestampedOnRetryWhileClientEventIdSurvives() {
		service.accept(buy(3));
		api.failNextPost = true;
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		GeTradeEvent event = api.postedBatches.get(0).get(0);
		assertEquals(36, java.util.UUID.fromString(event.getClientEventId()).toString().length());
		assertTrue(event.getPublishedAt() != null && !event.getPublishedAt().isEmpty());
		assertTrue(Instant.parse(event.getPublishedAt()).isAfter(T0));
	}
}
