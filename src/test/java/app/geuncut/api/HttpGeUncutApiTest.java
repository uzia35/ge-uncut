package app.geuncut.api;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.Flip;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpGeUncutApiTest {
	private static final String TOKEN = "gu_test_token";

	private MockWebServer server;
	private ScheduledExecutorService executor;
	private HttpGeUncutApi api;

	@Before
	public void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
		GeUncutConfig config = new GeUncutConfig() {
			@Override
			public String apiToken() {
				return TOKEN;
			}

			@Override
			public String apiBase() {
				String base = server.url("/").toString();
				return base.substring(0, base.length() - 1);
			}
		};
		executor = Executors.newSingleThreadScheduledExecutor();
		api = new HttpGeUncutApi(new OkHttpClient(), new Gson(), config, executor);
	}

	@After
	public void tearDown() throws Exception {
		server.shutdown();
		executor.shutdownNow();
	}

	@Test
	public void fetchFlipsParsesTheResponseAndAuthenticates() throws Exception {
		server.enqueue(new MockResponse().setBody(
				"{\"flips\":[{\"item_id\":20997,\"name\":\"Twisted bow\",\"buy_price\":1480000000,"
						+ "\"target_sell_price\":1560000000,\"quantity\":1,\"total_profit\":48000000,"
						+ "\"roi_per_day\":1.2,\"strategy\":\"standard\"}]}"));

		AtomicReference<FlipsResponse> received = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> {
			received.set(response);
			done.countDown();
		}, error -> done.countDown());

		assertTrue(done.await(2, TimeUnit.SECONDS));
		Flip flip = received.get().getFlips().get(0);
		assertEquals(20997, flip.getItemId());
		assertEquals("Twisted bow", flip.getName());
		assertEquals(1480000000L, flip.getBuyPrice());

		RecordedRequest recorded = server.takeRequest();
		assertEquals("GET", recorded.getMethod());
		assertEquals("/api/plugin/flips?scan_type=standard", recorded.getPath());
		assertEquals("Bearer " + TOKEN, recorded.getHeader("Authorization"));
	}

	@Test
	public void unauthorizedResponseReportsTheRelinkMessage() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED).setBody("{}"));

		AtomicReference<ApiFailure> error = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> done.countDown(), failure -> {
			error.set(failure);
			done.countDown();
		});

		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertTrue(error.get().isUnauthorized());
		assertEquals("This plugin is no longer linked. Link it again from the panel.", error.get().getMessage());
	}

	@Test
	public void transientErrorsRetryUntilTheBudgetThenReport() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE).setBody("{}"));

		AtomicReference<ApiFailure> error = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> done.countDown(), failure -> {
			error.set(failure);
			done.countDown();
		});

		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, error.get().getStatusCode());
		assertEquals("geuncut.app error 503", error.get().getMessage());
		assertEquals(3, server.getRequestCount());
	}

	@Test
	public void transientErrorRecoversOnRetry() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE).setBody("{}"));
		server.enqueue(new MockResponse().setBody("{\"flips\":[]}"));

		AtomicReference<FlipsResponse> received = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> {
			received.set(response);
			done.countDown();
		}, failure -> done.countDown());

		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertTrue(received.get().getFlips().isEmpty());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void unauthorizedIsNeverRetried() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED).setBody("{}"));

		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> done.countDown(), failure -> done.countDown());

		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(1, server.getRequestCount());
	}

	@Test
	public void unreachableServerReportsUnreachable() throws Exception {
		server.shutdown();

		AtomicReference<ApiFailure> error = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> done.countDown(), failure -> {
			error.set(failure);
			done.countDown();
		});

		assertTrue(done.await(5, TimeUnit.SECONDS));
		assertEquals(ApiFailure.NETWORK_FAILURE, error.get().getStatusCode());
		assertEquals("geuncut.app is unreachable", error.get().getMessage());
	}

	@Test
	public void malformedBodyReportsUnexpectedResponse() throws Exception {
		server.enqueue(new MockResponse().setBody("not json"));

		AtomicReference<ApiFailure> error = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchFlips("standard", response -> done.countDown(), failure -> {
			error.set(failure);
			done.countDown();
		});

		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertEquals("Unexpected response from geuncut.app", error.get().getMessage());
	}

	@Test
	public void postGeEventsSendsSnakeCaseFieldsAndAuthenticates() throws Exception {
		server.enqueue(new MockResponse().setBody("{}"));

		List<GeTradeEvent> events = Collections.singletonList(GeTradeEvent.builder()
				.accountHash("acct-1")
				.idempotencyKey("acct-1:0:20997:BUY:3:1751719200000")
				.itemId(20997)
				.side("buy")
				.quantity(3)
				.priceEach(1480000000)
				.slot(0)
				.occurredAt("2026-07-05T12:00:00Z")
				.build());

		CountDownLatch done = new CountDownLatch(1);
		api.postGeEvents(events, done::countDown, error -> done.countDown());

		assertTrue(done.await(2, TimeUnit.SECONDS));
		RecordedRequest recorded = server.takeRequest();
		assertEquals("POST", recorded.getMethod());
		assertEquals("/api/plugin/ge-events", recorded.getPath());
		assertEquals("Bearer " + TOKEN, recorded.getHeader("Authorization"));
		String body = recorded.getBody().readUtf8();
		assertTrue(body.contains("\"account_hash\":\"acct-1\""));
		assertTrue(body.contains("\"idempotency_key\""));
		assertTrue(body.contains("\"price_each\":1480000000"));
		assertTrue(body.contains("\"occurred_at\":\"2026-07-05T12:00:00Z\""));
	}

	@Test
	public void startLinkIsAnonymousAndParsesTheSession() throws Exception {
		server.enqueue(new MockResponse().setBody(
				"{\"user_code\":\"ABCD-1234\",\"device_code\":\"device-secret\",\"expires_in_seconds\":600}"));

		AtomicReference<LinkSession> received = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.startLink(session -> {
			received.set(session);
			done.countDown();
		}, error -> done.countDown());

		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertEquals("ABCD-1234", received.get().getUserCode());
		assertEquals("device-secret", received.get().getDeviceCode());
		assertEquals(600, received.get().getExpiresInSeconds());

		RecordedRequest recorded = server.takeRequest();
		assertEquals("POST", recorded.getMethod());
		assertEquals("/api/plugin/link/start", recorded.getPath());
		assertNull(recorded.getHeader("Authorization"));
	}

	@Test
	public void pollLinkRoutesPendingAndDeliveredStates() throws Exception {
		server.enqueue(new MockResponse().setBody("{\"pending\":true}"));
		server.enqueue(new MockResponse().setBody("{\"pending\":false,\"token\":\"gu_delivered\"}"));

		CountDownLatch pendingSeen = new CountDownLatch(1);
		api.pollLink("device-secret", token -> pendingSeen.countDown(), pendingSeen::countDown,
				error -> pendingSeen.countDown());
		assertTrue(pendingSeen.await(2, TimeUnit.SECONDS));

		AtomicReference<String> received = new AtomicReference<>();
		CountDownLatch delivered = new CountDownLatch(1);
		api.pollLink("device-secret", token -> {
			received.set(token);
			delivered.countDown();
		}, delivered::countDown, error -> delivered.countDown());

		assertTrue(delivered.await(2, TimeUnit.SECONDS));
		assertEquals("gu_delivered", received.get());

		server.takeRequest();
		RecordedRequest second = server.takeRequest();
		assertEquals("/api/plugin/link/poll", second.getPath());
		assertTrue(second.getBody().readUtf8().contains("\"device_code\":\"device-secret\""));
	}
}
