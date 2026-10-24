package app.geuncut.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import app.geuncut.dto.ItemHistory;
import app.geuncut.dto.ItemPrice;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.ItemChartData.Range;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfferPriceOverlayChartTest {
	private static final ItemHistory DATA = new ItemHistory(Collections.emptyList(), Arrays.asList(
			new ItemHistory.Candle(null, "2026-10-20", 3_000_000_000L, 3_010_000_000L),
			new ItemHistory.Candle(null, "2026-10-22", 3_020_000_000L, null)));

	@Test
	public void inexpensiveShortNameItemHasOneLargerFullWidthChartControl() throws Exception {
		String now = java.time.Instant.now().toString();
		ItemHistory history = new ItemHistory(Collections.emptyList(), Collections.singletonList(
				new ItemHistory.Candle(null, "2026-10-23", 5L, 6L, 0L, 0L)));
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(1511, new ItemPrice(1511, 5L, 6L, now, now), "Logs", null,
				new Snapshot(1511, Status.READY, history));
		BufferedImage collapsed = paint(overlay);
		assertEquals(232, collapsed.getWidth());
		assertEquals(104, collapsed.getHeight());
		assertTrue(ranges(overlay).isEmpty());
		openChart(overlay);
		BufferedImage expanded = paint(overlay);
		assertEquals(collapsed.getWidth(), expanded.getWidth());
		assertEquals(294, expanded.getHeight());
		assertTrue(ranges(overlay).get(Range.DAY).x > 70);
		Rectangle button = (Rectangle) field(overlay, "expandBounds");
		assertEquals(collapsed.getWidth() - 12, button.width);
		assertEquals(28, button.height);
		assertEquals(false, Arrays.stream(OfferPriceOverlay.class.getDeclaredFields())
				.anyMatch(field -> field.getName().equals("detailsBounds") || field.getName().equals("detailsOpen")));

	}

	@Test
	public void chartRangesWorkAtMovedOverlayCoordinates() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		openChart(overlay);
		BufferedImage image = paint(overlay);
		assertTrue(image.getHeight() < 334);
		assertTrue(image.getWidth() <= 512);
		overlay.getBounds().setLocation(43, 27);
		for (Range range : Range.values()) {
			assertTrue(overlay.handleChartClick(center(overlay, ranges(overlay).get(range)), id -> fail("not a retry")));
			assertEquals(range, field(overlay, "range"));
			paint(overlay);
		}
		// Hover cards use the same moved-overlay coordinates as the controls.
		overlay.setMouse(new Point(43 + 280, 27 + 190));
		paint(overlay);
		assertFalse(overlay.handleChartClick(new Point(-1, -1), id -> fail()));
	}


	@Test
	public void newItemClearsOldRangeAndRejectsMismatchedHistory() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		openChart(overlay);
		paint(overlay);
		overlay.handleChartClick(center(overlay, ranges(overlay).get(Range.YEAR)), id -> fail());
		overlay.setMouse(new Point(280, 190));
		overlay.show(4151, null, "Abyssal whip", null, new Snapshot(536, Status.READY, DATA));
		assertNull(field(overlay, "history"));
		assertNull(field(overlay, "mouse"));
		assertEquals(Range.MONTH, field(overlay, "range"));
		assertTrue(ranges(overlay).isEmpty());
		overlay.clear();
		assertFalse(overlay.handleChartClick(new Point(50, 50), id -> fail()));
		Graphics2D g = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { assertNull(overlay.render(g)); } finally { g.dispose(); }
	}

	@Test
	public void loadingEmptyErrorRetryAndSinglePointRenderWithoutPrices() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		for (Status status : new Status[] { Status.LOADING, Status.ERROR, Status.READY }) {
			overlay.show(536, null, "Dragon bones", null,
					new Snapshot(536, status, new ItemHistory(Collections.emptyList(), Collections.emptyList())));
			openChart(overlay);
			paint(overlay);
			if (status == Status.ERROR) {
				AtomicInteger retry = new AtomicInteger();
				assertTrue(overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "retryBounds")), retry::set));
				assertEquals(536, retry.get());
			} else {
				assertNull(field(overlay, "retryBounds"));
			}
		}
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY,
				new ItemHistory(null, Collections.singletonList(DATA.getDaily().get(0)))));
		paint(overlay);
	}

	@Test
	public void volumeWithoutPricesStillRendersAndLongQuotesFit() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		ItemHistory history = new ItemHistory(null, Arrays.asList(
				new ItemHistory.Candle(null, "2026-10-21", null, null, 0L, 5L),
				new ItemHistory.Candle(null, "2026-10-22", null, null, null, 12L)),
				new ItemHistory.ItemFacts(null, null, null, null));
		overlay.show(536, new ItemPrice(536, 3_000_000_000L, 3_001_000_000L, null, null),
				"A very long item name that should fit beside the facts", null,
				new Snapshot(536, Status.READY, history));
		openChart(overlay);
		overlay.setMouse(new Point(280, 170));
		BufferedImage image = paint(overlay);
		assertTrue(image.getHeight() < 334);
		assertTrue(image.getWidth() <= 512);
	}



	@Test
	public void compactExpansionIsExplicitAndResetsAcrossSelectionAndDisable() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		BufferedImage compact = paint(overlay);
		assertEquals(false, field(overlay, "expanded"));
		assertTrue(compact.getWidth() < 320);
		assertEquals(104, compact.getHeight());
		assertTrue(ranges(overlay).isEmpty());
		assertNull(field(overlay, "retryBounds"));
		assertFalse(overlay.handleChartClick(new Point(280, 150), id -> fail()));
		overlay.getBounds().setLocation(43, 27);
		assertTrue(overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail()));
		assertTrue(ranges(overlay).isEmpty());
		BufferedImage expanded = paint(overlay);
		assertEquals(compact.getWidth(), expanded.getWidth());
		assertEquals(294, expanded.getHeight());
		for (int y = 0; y < 68; y++) {
			for (int x = 0; x < compact.getWidth(); x++) { assertEquals(compact.getRGB(x, y), expanded.getRGB(x, y)); }
		}
		Point oldRangeTarget = center(overlay, ranges(overlay).get(Range.YEAR));
		overlay.handleChartClick(center(overlay, ranges(overlay).get(Range.WEEK)), id -> fail());
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		assertEquals(true, field(overlay, "expanded"));
		overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail());
		assertEquals(Range.WEEK, field(overlay, "range"));
		assertTrue(ranges(overlay).isEmpty());
		assertFalse(overlay.handleChartClick(oldRangeTarget, id -> fail()));
		assertEquals(104, paint(overlay).getHeight());
		assertTrue(ranges(overlay).isEmpty());
		overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail());
		overlay.show(4151, null, "Abyssal whip", null, new Snapshot(4151, Status.READY, DATA));
		assertEquals(false, field(overlay, "expanded"));
		paint(overlay);
		overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail());
		overlay.show(4151, null, "Abyssal whip", null, null, false);
		assertEquals(false, field(overlay, "expanded"));
		assertNull(field(overlay, "expandBounds"));
		overlay.clear();
		assertNull(field(overlay, "expandBounds"));
		overlay.show(4151, null, "Abyssal whip", null, new Snapshot(4151, Status.READY, DATA));
		assertEquals(false, field(overlay, "expanded"));
		assertEquals(compact.getWidth(), paint(overlay).getWidth());
	}

	@Test
	public void priceAndVolumeHoverShareTheSamePointCardAndLeaveSummaryVisible() throws Exception {
		ItemHistory history = new ItemHistory(Collections.singletonList(
				new ItemHistory.Candle("2026-10-23T01:15:00", null, 3200L, 3286L, 0L, null)),
				Collections.singletonList(new ItemHistory.Candle(null, "2026-10-23", 3200L, 3286L, 1652L, 9619L)));
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, history));
		openChart(overlay);
			for (Range range : Range.values()) {
				overlay.handleChartClick(center(overlay, ranges(overlay).get(range)), id -> fail());
				overlay.setMouse(null);
				BufferedImage clean = paint(overlay);
				overlay.setMouse(new Point(170, 177));
				BufferedImage priceHover = paint(overlay);
				overlay.setMouse(new Point(170, 259));
				BufferedImage volumeHover = paint(overlay);
				for (int y = 0; y < clean.getHeight(); y++) {
					for (int x = 0; x < clean.getWidth(); x++) {

						if (y < 104) {
							assertEquals(clean.getRGB(x, y), priceHover.getRGB(x, y));
							assertEquals(clean.getRGB(x, y), volumeHover.getRGB(x, y));
						}
					}
				}
			}
	}

	@Test
	public void translucentBackdropAndButtonHoverUseTheSameScaledHitBounds() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		for (int mode = 0; mode < 2; mode++) {
			BufferedImage normal = paint(overlay);
			assertEquals(220, normal.getRGB(3, 35) >>> 24);
			overlay.getBounds().setLocation(43, 27);
			Rectangle button = (Rectangle) field(overlay, "expandBounds");
			Point click = center(overlay, button);
			overlay.setMouse(click);
			BufferedImage hover = paint(overlay);
			assertNotEquals(normal.getRGB(button.x + 4, button.y + 8), hover.getRGB(button.x + 4, button.y + 8));
			BufferedImage scaled = new BufferedImage(650, 450, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = scaled.createGraphics();
			try { graphics.scale(1.25, 1.25); overlay.render(graphics); } finally { graphics.dispose(); }
			assertNotEquals(0, scaled.getRGB((int) ((button.x + 4) * 1.25), (int) ((button.y + 8) * 1.25)));
			assertTrue(overlay.handleChartClick(click, id -> fail()));
			overlay.setMouse(null);
			BufferedImage active = paint(overlay);
			assertNotEquals(normal.getRGB(button.x + 4, button.y + 8), active.getRGB(button.x + 4, button.y + 8));
			overlay.handleChartClick(click, id -> fail());
			paint(overlay);
			overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail());
		}
	}

	@Test
	public void closeIsDistinctFromChartToggleAndClearsEveryHitTargetAtMovedCoordinates() throws Exception {
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		BufferedImage normal = paint(overlay);
		overlay.getBounds().setLocation(43, 27);
		Rectangle close = (Rectangle) field(overlay, "closeBounds");
		Point closeClick = center(overlay, close);
		overlay.setMouse(closeClick);
		BufferedImage hover = paint(overlay);
		assertNotEquals(normal.getRGB(close.x + 2, close.y + 2), hover.getRGB(close.x + 2, close.y + 2));
		openChart(overlay);
		Point hide = center(overlay, (Rectangle) field(overlay, "expandBounds"));
		overlay.handleChartClick(hide, id -> fail());
		assertFalse(overlay.isDismissed(536));
		assertEquals(104, paint(overlay).getHeight());
		assertTrue(overlay.handleChartClick(closeClick, id -> fail()));
		assertTrue(overlay.isDismissed(536));
		assertTrue(ranges(overlay).isEmpty());
		assertNull(field(overlay, "closeBounds"));
		assertNull(field(overlay, "expandBounds"));
		assertFalse(overlay.handleChartClick(hide, id -> fail()));
		// Ordinary same-item refreshes cannot undo the dismissal.
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		Graphics2D g = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { assertNull(overlay.render(g)); } finally { g.dispose(); }
		overlay.clear();
		overlay.show(536, null, "Dragon bones", null, new Snapshot(536, Status.READY, DATA));
		assertFalse(overlay.isDismissed(536));
		assertEquals(104, paint(overlay).getHeight());
	}

	static BufferedImage paint(OfferPriceOverlay overlay) {
		BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Dimension size;
		try { size = overlay.render(g); } finally { g.dispose(); }
		assertNotNull(size);
		assertTrue(size.width < image.getWidth());
		assertTrue(size.height < image.getHeight());
		overlay.getBounds().setSize(size);
		return image.getSubimage(0, 0, size.width, size.height);
	}

	private static void openChart(OfferPriceOverlay overlay) throws Exception {
		paint(overlay);
		if (!(boolean) field(overlay, "expanded")) {
			assertTrue(overlay.handleChartClick(center(overlay, (Rectangle) field(overlay, "expandBounds")), id -> fail()));
			paint(overlay);
		}
	}

	private static Object field(Object target, String name) throws Exception {
		java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	@SuppressWarnings("unchecked")
	private static Map<Range, Rectangle> ranges(OfferPriceOverlay overlay) throws Exception {
		return (Map<Range, Rectangle>) field(overlay, "rangeBounds");
	}

	private static Point center(OfferPriceOverlay overlay, Rectangle local) {
		return new Point(overlay.getBounds().x + local.x + local.width / 2,
				overlay.getBounds().y + local.y + local.height / 2);
	}

	/** Optional render harness: args are a captured public item-detail JSON file and output directory. */
	public static void main(String[] args) throws Exception {
		Gson gson = new Gson();
		JsonObject json = gson.fromJson(Files.readString(Path.of(args[0])), JsonObject.class);
		JsonObject item = json.getAsJsonObject("item");
		int id = item.get("item_id").getAsInt();
		ItemHistory history = gson.fromJson(json, ItemHistory.class);
		ItemPrice price = new ItemPrice(id, item.get("latest_low").getAsLong(), item.get("latest_high").getAsLong(),
				item.get("low_time").getAsString(), item.get("high_time").getAsString());
		OfferPriceOverlay overlay = new OfferPriceOverlay();
		overlay.show(id, price, item.get("name").getAsString(), null, new Snapshot(id, Status.READY, history));
		Files.createDirectories(Path.of(args[1]));
		openChart(overlay);
		for (Range range : Range.values()) {
			overlay.handleChartClick(center(overlay, ranges(overlay).get(range)), ignored -> {});
			ImageIO.write(paint(overlay), "png", new File(args[1], "item-" + id + "-" + range.label + ".png"));
		}
		overlay.handleChartClick(center(overlay, ranges(overlay).get(Range.MONTH)), ignored -> {});
		overlay.setMouse(new Point(280, 190));
		ImageIO.write(paint(overlay), "png", new File(args[1], "item-" + id + "-hover.png"));
		for (Status status : new Status[] { Status.LOADING, Status.ERROR, Status.READY }) {
			overlay.show(id, price, item.get("name").getAsString(), null,
					new Snapshot(id, status, new ItemHistory(Collections.emptyList(), Collections.emptyList())));
			ImageIO.write(paint(overlay), "png", new File(args[1], "state-" + status + ".png"));
		}
	}
}
