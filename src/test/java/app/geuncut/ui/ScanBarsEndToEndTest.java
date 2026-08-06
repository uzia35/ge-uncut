package app.geuncut.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.service.impl.FlipsServiceImpl;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the user picks in the panel has to arrive at the server as a query parameter, and
 * what the server answers has to arrive back in the panel. The pieces were covered
 * separately (panel state, then URL building) which left the seam between them untested:
 * a picker wired to nothing would still have passed both halves.
 *
 * Runs the real HttpGeUncutApi and FlipsServiceImpl against a MockWebServer, driven by a
 * real FlipsPanel, so the only fake here is the server.
 */
public class ScanBarsEndToEndTest {
	private MockWebServer server;
	private ScheduledExecutorService executor;
	private FlipsServiceImpl flips;
	private FlipsPanel panel;

	@Before
	public void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
		GeUncutConfig config = new GeUncutConfig() {
			@Override
			public String apiToken() {
				return "gu_test_token";
			}
		};
		executor = Executors.newSingleThreadScheduledExecutor();
		String base = server.url("/").toString();
		base = base.substring(0, base.length() - 1);
		flips = new FlipsServiceImpl(new HttpGeUncutApi(new OkHttpClient(), new Gson(), config, executor, base));
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		LongConsumer noopArchive = id -> {
		};
		panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
	}

	private static ItemIconLoader stubIcons() {
		return new ItemIconLoader(null, null) {
			@Override
			Image sprite(int itemId, Runnable onLoaded) {
				return new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			}

			@Override
			void load(int itemId, String name, javax.swing.JLabel label, int size) {
			}
		};
	}

	@After
	public void tearDown() throws Exception {
		server.shutdown();
		executor.shutdownNow();
	}

	private String scanOnce(String body) throws Exception {
		server.enqueue(new MockResponse().setBody(body));
		CountDownLatch done = new CountDownLatch(1);
		flips.fetch(panel.scanRequest(), response -> done.countDown(), failure -> done.countDown());
		assertTrue("the scan never came back", done.await(3, TimeUnit.SECONDS));
		return server.takeRequest().getPath();
	}

	@Test
	public void aBarPickedInThePanelReachesTheServer() throws Exception {
		assertTrue("an untouched panel must not pin a bar the user never chose",
				!scanOnce("{\"flips\":[]}").contains("min_profit"));

		panel.applySavedScanBars(1_000_000L, 3.0);
		String path = scanOnce("{\"flips\":[]}");
		assertTrue("the profit bar never left the panel: " + path, path.contains("min_profit=1000000"));
		assertTrue("the margin bar never left the panel: " + path, path.contains("min_roi=3"));

		// Any / Any is the one selection a truthiness bug would silently drop on the way out.
		panel.applySavedScanBars(0L, 0.0);
		String any = scanOnce("{\"flips\":[]}");
		assertTrue("Any profit was dropped instead of sent as 0: " + any, any.contains("min_profit=0"));
		assertTrue("Any margin was dropped instead of sent as 0: " + any, any.contains("min_roi=0"));
	}

	@Test
	public void theServersAnswerLandsBackOnThePickers() throws Exception {
		AtomicReference<FlipsResponse> seen = new AtomicReference<>();
		server.enqueue(new MockResponse().setBody(
				"{\"flips\":[],\"my_capital\":50000000,\"my_min_profit\":750000,\"my_min_roi\":1.5,"
						+ "\"min_profit_floor\":1000000,\"min_roi_floor\":3.0}"));
		CountDownLatch done = new CountDownLatch(1);
		flips.fetch(panel.scanRequest(), response -> {
			seen.set(response);
			done.countDown();
		}, failure -> done.countDown());
		assertTrue(done.await(3, TimeUnit.SECONDS));
		server.takeRequest();

		FlipsResponse response = seen.get();
		panel.applyScanBars(true, response.getMyMinProfit(), response.getMyMinRoi(),
				response.getMinProfitFloor(), response.getMinRoiFloor());

		assertEquals("the website's saved profit bar did not become the panel's selection",
				Long.valueOf(750_000L), panel.selectedMinProfit());
		assertEquals(Double.valueOf(1.5), panel.selectedMinRoi());

		// And it round-trips: the value the server handed back is what the next scan sends.
		assertTrue(scanOnce("{\"flips\":[]}").contains("min_profit=750000"));
	}
}
