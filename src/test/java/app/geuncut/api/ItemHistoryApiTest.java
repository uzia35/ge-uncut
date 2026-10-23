package app.geuncut.api;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.ItemHistory;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemHistoryApiTest {
	private MockWebServer server;
	private ScheduledExecutorService executor;
	private HttpGeUncutApi api;

	@Before
	public void setup() throws Exception {
		server = new MockWebServer();
		server.start();
		executor = Executors.newSingleThreadScheduledExecutor();
		String base = server.url("/").toString();
		api = new HttpGeUncutApi(new OkHttpClient(), new Gson(), new GeUncutConfig() {}, executor,
				base.substring(0, base.length() - 1));
	}

	@After
	public void close() throws Exception {
		server.shutdown();
		executor.shutdownNow();
	}

	@Test
	public void readsPublicWebsiteCandlesAndItemFactsWithLongAndMissingValues() throws Exception {
		server.enqueue(new MockResponse().setBody("{\"intraday_candles\":[{\"bucket_ts\":\"2026-10-22T00:00:00\","
				+ "\"avg_low_price\":3000000000,\"avg_high_price\":null,\"low_price_volume\":4000000000,"
				+ "\"high_price_volume\":0}],\"daily_candles\":[],\"item\":{\"members\":false,"
				+ "\"buy_limit\":10000,\"high_alch\":null,\"low_alch\":64}}"));
		AtomicReference<ItemHistory> received = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchItemHistory(20997, history -> { received.set(history); done.countDown(); }, failure -> done.countDown());
		assertTrue(done.await(3, TimeUnit.SECONDS));
		assertNotNull(received.get());
		assertEquals(Long.valueOf(3_000_000_000L), received.get().getIntraday().get(0).getBuy());
		assertNull(received.get().getIntraday().get(0).getSell());
		assertEquals(Long.valueOf(4_000_000_000L), received.get().getIntraday().get(0).getBuyVolume());
		assertEquals(Long.valueOf(0), received.get().getIntraday().get(0).getSellVolume());
		assertEquals(Boolean.FALSE, received.get().getItem().getMembers());
		assertEquals(Long.valueOf(10_000), received.get().getItem().getBuyLimit());
		assertNull(received.get().getItem().getHighAlch());
		assertEquals(Long.valueOf(64), received.get().getItem().getLowAlch());
		assertEquals("/api/market/items/20997", server.takeRequest().getPath());
	}

	@Test
	public void emptyArraysAreValidHistory() throws Exception {
		server.enqueue(new MockResponse().setBody("{\"intraday_candles\":[],\"daily_candles\":[]}"));
		AtomicReference<ItemHistory> received = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		api.fetchItemHistory(536, history -> { received.set(history); done.countDown(); }, failure -> done.countDown());
		assertTrue(done.await(3, TimeUnit.SECONDS));
		assertTrue(received.get().getDaily().isEmpty());
		assertNull(received.get().getItem());
	}

	@Test
	public void missingOrMalformedDataAnd404AreErrorsRatherThanEmptyCharts() throws Exception {
		for (MockResponse response : new MockResponse[] {
				new MockResponse().setBody("{}"), new MockResponse().setBody("{\"intraday_candles\":null,\"daily_candles\":[]}"),
				new MockResponse().setBody("not json"), new MockResponse().setResponseCode(404) }) {
			server.enqueue(response);
			AtomicReference<ApiFailure> error = new AtomicReference<>();
			CountDownLatch done = new CountDownLatch(1);
			api.fetchItemHistory(536, history -> done.countDown(), failure -> { error.set(failure); done.countDown(); });
			assertTrue(done.await(3, TimeUnit.SECONDS));
			assertNotNull(error.get());
		}
	}
}
