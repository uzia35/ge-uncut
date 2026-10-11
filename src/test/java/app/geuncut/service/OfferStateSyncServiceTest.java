package app.geuncut.service;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.OfferState;
import app.geuncut.service.impl.OfferStateSyncServiceImpl;
import app.geuncut.tracker.OfferLog;
import app.geuncut.tracker.impl.InMemoryOfferLog;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class OfferStateSyncServiceTest {
	private static final int TBOW = 20997;

	private MockGeUncutApi api;
	private OfferLog log;
	private OfferStateSyncService service;
	private Runnable flushTick;
	private List<Integer> booked;

	@Before
	public void setUp() {
		api = new MockGeUncutApi();
		log = new InMemoryOfferLog();
		booked = new ArrayList<>();
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		service = new OfferStateSyncServiceImpl(api, log, executor);
		service.setOnFillsBooked(booked::add);
		flushTick = startAndCaptureTick(service, executor);
	}

	private static Runnable startAndCaptureTick(OfferStateSyncService svc, ScheduledExecutorService executor) {
		svc.start(() -> "acct-1");
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		return tick.getValue();
	}

	private Runnable restarted(MockGeUncutApi freshApi) {
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		OfferStateSyncService svc = new OfferStateSyncServiceImpl(freshApi, log, executor);
		return startAndCaptureTick(svc, executor);
	}

	private static OfferState buying(int filled) {
		return OfferState.builder()
				.installId("install-0f2c1a9b")
				.slot(0)
				.state("buying")
				.itemId(TBOW)
				.side("buy")
				.priceEach(1_000_000)
				.quantityTotal(1000)
				.quantityFilled(filled)
				.spent(filled * 1_000_000L)
				.observedAt("2026-07-05T12:00:00Z")
				.build();
	}

	private void record(int filled) {
		log.append("acct-1", buying(filled));
	}

	@Test
	public void recordedStatesFlushAsOneBatchWithTheAccount() {
		record(3);
		record(5);
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals("acct-1", api.postedStatesAccounts.get(0));
		List<OfferState> batch = api.postedBatches.get(0);
		assertEquals(2, batch.size());
		assertEquals("buying", batch.get(0).getState());
		assertEquals(3, batch.get(0).getQuantityFilled());
		assertEquals(3_000_000L, batch.get(0).getSpent());
		assertEquals(Long.valueOf(0), batch.get(0).getSeq());
		assertEquals(Long.valueOf(1), batch.get(1).getSeq());
		assertEquals("install-0f2c1a9b", batch.get(0).getInstallId());
	}

	@Test
	public void theStatesSerializeAsTheServerExpects() {
		record(3);
		flushTick.run();

		JsonObject json = new Gson().toJsonTree(api.postedBatches.get(0).get(0)).getAsJsonObject();
		assertEquals(0, json.get("seq").getAsLong());
		assertEquals("install-0f2c1a9b", json.get("install_id").getAsString());
		assertEquals(0, json.get("slot").getAsInt());
		assertEquals("buying", json.get("state").getAsString());
		assertEquals(TBOW, json.get("item_id").getAsInt());
		assertEquals("buy", json.get("side").getAsString());
		assertEquals(1_000_000L, json.get("price_each").getAsLong());
		assertEquals(1000, json.get("quantity_total").getAsInt());
		assertEquals(3, json.get("quantity_filled").getAsInt());
		assertEquals(3_000_000L, json.get("spent").getAsLong());
		assertEquals("2026-07-05T12:00:00Z", json.get("observed_at").getAsString());
	}

	@Test
	public void emptyLogDoesNotPost() {
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
	}

	@Test
	public void aSuccessfulFlushAdvancesTheCursorAndKeepsTheLog() {
		record(3);
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals(1, log.read("acct-1", 0, Integer.MAX_VALUE).getEntries().size());
		assertEquals(1, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aTransientFailureLeavesTheCursorAndRetriesInOrder() {
		record(1);
		record(2);
		api.failNextPost = true;
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(0, log.deliveredOffset("acct-1"));

		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantityFilled());
		assertEquals(2, api.postedBatches.get(0).get(1).getQuantityFilled());
		assertEquals(2, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aBacklogDrainsInBatchesOfAtMostTwoHundred() {
		for (int i = 1; i <= 450; i++) {
			record(i);
		}
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(200, api.postedBatches.get(0).size());
		assertEquals(200, log.deliveredOffset("acct-1"));

		flushTick.run();
		flushTick.run();
		flushTick.run();
		assertEquals(3, api.postedBatches.size());
		assertEquals(200, api.postedBatches.get(1).size());
		assertEquals(50, api.postedBatches.get(2).size());
		assertEquals(450, log.deliveredOffset("acct-1"));
		assertEquals(201, api.postedBatches.get(1).get(0).getQuantityFilled());
		assertEquals(401, api.postedBatches.get(2).get(0).getQuantityFilled());
	}

	@Test
	public void anUnauthorizedFlushKeepsTheCursorAndGoesQuiet() {
		record(3);
		api.failNextPost = true;
		api.failure = ApiFailure.http(HttpURLConnection.HTTP_UNAUTHORIZED, "not linked");
		flushTick.run();

		assertTrue(api.postedBatches.isEmpty());
		assertEquals(0, log.deliveredOffset("acct-1"));

		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());
		assertEquals(0, log.deliveredOffset("acct-1"));
	}

	@Test
	public void statesRecordedWhileUnlinkedAreSentAfterLinking() {
		service.setSendGate(() -> false);
		record(3);
		record(4);
		flushTick.run();
		assertTrue(api.postedBatches.isEmpty());

		service.setSendGate(() -> true);
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(2, api.postedBatches.get(0).size());
	}

	@Test
	public void oneRequestIsInFlightAtATime() {
		record(1);
		api.deferNextPost = true;
		flushTick.run();
		record(2);
		flushTick.run();

		assertTrue(api.postedBatches.isEmpty());
		api.firePendingPostFailure();
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(2, api.postedBatches.get(0).size());
	}

	@Test
	public void aFreshSessionReplaysTheWholeLogOnceThenContinues() {
		record(1);
		record(2);
		flushTick.run();
		assertEquals(2, log.deliveredOffset("acct-1"));

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable tick = restarted(next);
		tick.run();

		assertEquals(1, next.postedBatches.size());
		assertEquals(2, next.postedBatches.get(0).size());
		assertEquals(1, next.postedBatches.get(0).get(0).getQuantityFilled());
		assertEquals(2, log.deliveredOffset("acct-1"));

		tick.run();
		assertEquals(1, next.postedBatches.size());

		record(3);
		tick.run();
		assertEquals(2, next.postedBatches.size());
		assertEquals(3, next.postedBatches.get(1).get(0).getQuantityFilled());
		assertEquals(3, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aLongReplayIsChunked() {
		for (int i = 1; i <= 260; i++) {
			record(i);
		}
		flushTick.run();
		flushTick.run();
		assertEquals(260, log.deliveredOffset("acct-1"));

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable tick = restarted(next);
		tick.run();
		tick.run();
		tick.run();

		assertEquals(2, next.postedBatches.size());
		assertEquals(200, next.postedBatches.get(0).size());
		assertEquals(60, next.postedBatches.get(1).size());
		assertEquals(201, next.postedBatches.get(1).get(0).getQuantityFilled());
	}

	@Test
	public void bookedFillsTriggerTheRefreshCallback() {
		api.fillsBooked = 2;
		record(3);
		flushTick.run();

		assertEquals(1, booked.size());
		assertEquals(Integer.valueOf(2), booked.get(0));
	}

	@Test
	public void noBookedFillsMeansNoRefresh() {
		record(3);
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertTrue(booked.isEmpty());
	}

	@Test
	public void aFailedPostNeverTriggersTheRefresh() {
		api.fillsBooked = 2;
		api.failNextPost = true;
		record(3);
		flushTick.run();

		assertTrue(booked.isEmpty());
	}

	@Test
	public void newStatesGoAheadOfTheReplay() {
		for (int i = 1; i <= 260; i++) {
			record(i);
		}
		flushTick.run();
		flushTick.run();

		MockGeUncutApi next = new MockGeUncutApi();
		Runnable tick = restarted(next);
		record(999);
		tick.run();

		assertEquals(1, next.postedBatches.size());
		assertEquals(999, next.postedBatches.get(0).get(0).getQuantityFilled());
		tick.run();
		assertEquals(1, next.postedBatches.get(1).get(0).getQuantityFilled());
	}

	@Test
	public void aRejectedBatchIsRetriedOneStateAtATimeAndTheBadStateIsSkipped() {
		record(1);
		record(2);
		record(3);
		api.failure = ApiFailure.http(422, "bad state");
		api.failNextPost = true;
		flushTick.run();
		assertEquals(0, log.deliveredOffset("acct-1"));

		flushTick.run();
		assertEquals(1, api.postedBatches.get(0).size());
		assertEquals(1, log.deliveredOffset("acct-1"));

		api.failNextPost = true;
		flushTick.run();
		assertEquals(1, log.deliveredOffset("acct-1"));

		api.failNextPost = true;
		flushTick.run();
		assertEquals(1, api.postedBatches.size());
		assertEquals(2, log.deliveredOffset("acct-1"));

		flushTick.run();
		assertEquals(2, api.postedBatches.size());
		assertEquals(3, api.postedBatches.get(1).get(0).getQuantityFilled());
		assertEquals(3, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aServerErrorIsRetriedWithoutSkipping() {
		record(1);
		api.failure = ApiFailure.http(500, "down");
		api.failNextPost = true;
		flushTick.run();
		flushTick.run();

		assertEquals(1, api.postedBatches.size());
		assertEquals(1, api.postedBatches.get(0).get(0).getQuantityFilled());
	}

	@Test
	public void statesLeftAtLogoutStillUploadForTheLastAccount() {
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		OfferStateSyncService svc = new OfferStateSyncServiceImpl(api, log, executor);
		String[] current = { "acct-1" };
		svc.start(() -> current[0]);
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		tick.getValue().run();

		record(7);
		current[0] = null;
		tick.getValue().run();

		assertEquals(1, api.postedBatches.size());
		assertEquals("acct-1", api.postedStatesAccounts.get(0));
	}
}