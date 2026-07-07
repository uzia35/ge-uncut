package app.geuncut.service;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.geuncut.api.ApiFailure;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.impl.TradeSyncServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TradeSyncServiceTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");

	private MockGeUncutApi api;
	private ScheduledExecutorService executor;
	private TradeSyncService service;
	private Runnable flushTick;
	private boolean syncEnabled = true;

	@Before
	public void setUp() {
		api = new MockGeUncutApi();
		executor = mock(ScheduledExecutorService.class);
		GeUncutConfig config = new GeUncutConfig() {
			@Override
			public boolean syncTrades() {
				return syncEnabled;
			}
		};
		service = new TradeSyncServiceImpl(api, config, executor);
		service.start(() -> "acct-1");

		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		flushTick = tick.getValue();
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
	public void failedBatchIsRequeuedInOrderAndRetried() {
		service.accept(buy(1));
		service.accept(buy(2));
		api.failNextPost = true;
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(2, api.postedBatches.get(0).get(1).getQuantity());
	}

	@Test
	public void emptyQueueDoesNotPost() {
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void syncDisabledDropsFills() {
		syncEnabled = false;
		service.accept(buy(3));
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void unauthorizedFlushDropsTheBatchInsteadOfRetrying() {
		service.accept(buy(3));
		api.failNextPost = true;
		api.failure = ApiFailure.http(HttpURLConnection.HTTP_UNAUTHORIZED, "not linked");
		flushTick.run();

		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void queueIsBoundedDroppingOldestFirst() {
		for (int quantity = 1; quantity <= 250; quantity++) {
			service.accept(buy(quantity));
		}
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals(200, api.postedBatches.get(0).size());
		assertEquals(51, api.postedBatches.get(0).get(0).getQuantity());
		assertEquals(250, api.postedBatches.get(0).get(199).getQuantity());
	}

	@Test
	public void requeueDoesNotGrowTheQueueBeyondCap() {
		for (int quantity = 1; quantity <= 200; quantity++) {
			service.accept(buy(quantity));
		}
		// Hold the flush's POST so accepts can interleave the way a real outage allows.
		api.deferNextPost = true;
		flushTick.run();
		for (int quantity = 201; quantity <= 400; quantity++) {
			service.accept(buy(quantity));
		}
		api.firePendingPostFailure();

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(200, api.postedBatches.get(0).size());
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
}
