package app.geuncut.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import javax.imageio.ImageIO;
import javax.swing.JLabel;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.PositionsResponse;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FlipsPanelRenderTest {
	private static final int PANEL_WIDTH = net.runelite.client.ui.PluginPanel.PANEL_WIDTH;
	private static final LongConsumer noopArchive = id -> {
	};

	private static final String ACTIVE_FLIPS_JSON = "{\"summary\":{\"total_realized\":0,\"today_realized\":0," +
			"\"open_unrealized\":506000,\"open_count\":1,\"flips\":0,\"win_rate\":null,\"avg_roi\":null}," +
			"\"positions\":[{\"id\":42,\"item_id\":22124,\"item_name\":\"Superior dragon bones\",\"quantity\":1097," +
			"\"buy_price\":20065,\"sold_qty\":400,\"sold_avg_price\":21010,\"realized_profit\":210000," +
			"\"unrealized_profit\":506000,\"roi\":2.3,\"phase\":\"sell\",\"sell_reason\":\"target_hit\",\"price_stale\":false}]}";

	private static final String ARCHIVED_JSON = "{\"positions\":[" +
			"{\"id\":7,\"item_id\":231,\"item_name\":\"Snape grass\",\"quantity\":600,\"buy_price\":480}]}";

	private static final String MOVERS_JSON = "{\"risers\":[" +
			"{\"item_id\":1,\"name\":\"Hueycoatl hide vambraces\",\"change_pct\":21.8,\"price\":38213,\"volume_day\":124000}," +
			"{\"item_id\":2,\"name\":\"Watermelon seed\",\"change_pct\":31.6,\"price\":52,\"volume_day\":2400000}]," +
			"\"fallers\":[" +
			"{\"item_id\":3,\"name\":\"Ghorrock teleport (tablet)\",\"change_pct\":-40.3,\"price\":11890,\"volume_day\":8100}]}";

	private static final String FLIPS_JSON = "{\"flips\":[" +
			"{\"item_id\":1623,\"name\":\"Uncut diamond\",\"buy_price\":2512,\"target_sell_price\":2617," +
			"\"quantity\":10000,\"total_profit\":530000,\"profit_per_item\":53,\"position_cost\":25120000," +
			"\"roi\":2.1,\"roi_per_day\":0.7,\"entry_discount_pct\":4.0,\"strategy\":\"value\",\"members\":false," +
			"\"demand_trend\":{\"direction\":\"steady\",\"ratio\":0.8}," +
			"\"buy_fill\":{\"worst_case_hours\":0.23},\"sell_fill\":{\"worst_case_hours\":0.53}}," +
			"{\"item_id\":383,\"name\":\"Marlin\",\"buy_price\":3522,\"target_sell_price\":3927," +
			"\"quantity\":28392,\"total_profit\":9280000,\"profit_per_item\":327,\"position_cost\":100000000," +
			"\"roi\":9.3,\"roi_per_day\":3.1,\"entry_discount_pct\":10.3,\"strategy\":\"standard\",\"members\":true," +
			"\"horizon\":{\"recommended_days\":3}," +
			"\"demand_trend\":{\"direction\":\"rising\",\"ratio\":1.4}," +
			"\"news\":{\"direction\":\"up\",\"confidence\":80}," +
			"\"buy_fill\":{\"worst_case_hours\":108.0},\"sell_fill\":{\"worst_case_hours\":74.4}}" +
			"]}";

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

	@Test
	public void rendersUnlinkedAndLinkedPanels() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showFlips(response.getFlips(), false);
		BufferedImage unlinked = paint(panel);
		write(unlinked, "panel-unlinked.png");

		panel.showFlips(response.getFlips(), true);
		write(paint(panel), "panel-linked.png");

		assertTrue(countDistinctColors(unlinked) > 8);
	}

	@Test
	public void linkPromptSurvivesAccountFilterChanges() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showLinkPrompt();
		paint(panel);
		BufferedImage before = paint(panel);
		write(before, "panel-link-prompt.png");

		panel.renderFlips();
		BufferedImage after = paint(panel);
		assertTrue(pixelsEqual(before, after));
	}

	@Test
	public void rendersEachTab() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse flips = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		PositionsResponse positions = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showFlips(flips.getFlips(), true);
		panel.showActiveFlips(positions);

		java.util.List<app.geuncut.dto.GeOffer> offers = java.util.Arrays.asList(
				app.geuncut.dto.GeOffer.builder().slot(0).itemId(314).side("buy").state("bought")
						.quantityFilled(1000).quantityTotal(1000).priceEach(3).occurredAt("2020-01-01T00:00:00Z").build(),
				app.geuncut.dto.GeOffer.builder().slot(2).itemId(536).side("buy").state("buying")
						.quantityFilled(2550).quantityTotal(7500).priceEach(3100).occurredAt("2020-01-01T00:00:00Z").build());
		java.util.Map<Integer, String> names = new java.util.HashMap<>();
		names.put(314, "Feathers");
		names.put(536, "Dragon bones");
		panel.showOffers(offers, names);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		panel.showMovers(new Gson().fromJson(MOVERS_JSON, app.geuncut.dto.Movers.class));

		panel.toggleFlip(42);
		for (String tab : new String[] { "finder", "flips", "movers", "history" }) {
			panel.selectTab(tab);
			write(paint(panel), "panel-tab-" + tab + ".png");
		}
		panel.selectTab("flips");
		assertNotNull(findLabel(panel, "Not a flip"));
		assertNotNull(findLabel(panel, "Item details ↗"));
		panel.selectTab("history");
		assertNotNull(findLabel(panel, "Make it a flip"));
	}

	@Test
	public void flipCardExpandsAndCollapses() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showActiveFlips(new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class));

		assertTrue(findLabel(panel, "Not a flip") == null);
		panel.toggleFlip(42);
		assertNotNull(findLabel(panel, "Not a flip"));
		assertNotNull(findLabel(panel, "Item details ↗"));
		assertNotNull(findLabel(panel, "Total profit"));
		assertNotNull(findLabel(panel, "Avg profit / ea"));
		assertNotNull(findLabel(panel, "Unrealized P/L"));
		assertNotNull(findLabel(panel, "Realized so far"));
		assertNotNull(findLabel(panel, "Avg ROI"));
		panel.toggleFlip(42);
		assertTrue(findLabel(panel, "Not a flip") == null);
	}

	@Test
	public void sparseFlipsPaneDoesNotStretchHero() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showActiveFlips(new Gson().fromJson("{\"summary\":{},\"positions\":[]}", PositionsResponse.class));
		panel.selectTab("flips");
		BufferedImage image = paint(panel);
		write(image, "panel-flips-sparse.png");

		int red = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() - 30)).getRed();
		assertTrue("stats card stretched to fill the pane (red=" + red + ")", red < 0x2E);
	}

	@Test
	public void restoreClickCallsBackWithPositionId() {
		long[] captured = { -1 };
		LongConsumer capture = id -> captured[0] = id;
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, capture);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		JLabel restore = findLabel(panel, "Make it a flip");
		assertNotNull(restore);

		MouseEvent click = new MouseEvent(restore, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
		for (MouseListener listener : restore.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		assertEquals(7L, captured[0]);
	}

	@Test
	public void rendersLightThemePanel() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		try {
			FlipsPanel.applyTheme(true);
			FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
			panel.showFlips(response.getFlips(), true);
			BufferedImage light = paint(panel);
			write(light, "panel-light.png");
			assertTrue("light surface should be bright", new Color(light.getRGB(4, 4)).getRed() > 0xC0);
		} finally {
			FlipsPanel.applyTheme(false);
		}
	}

	@Test
	public void activeFlipCardShowsNotFlipAffordance() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		PositionsResponse response = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive);
		panel.showActiveFlips(response);
		panel.toggleFlip(42);
		write(paint(panel), "panel-active-flips.png");

		assertNotNull("expected a 'Not a flip' control on the expanded flip card",
				findLabel(panel, "Not a flip"));
	}

	@Test
	public void notFlipClickCallsBackWithPositionId() {
		long[] captured = { -1 };
		LongConsumer capture = id -> captured[0] = id;
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		PositionsResponse response = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, capture, noopArchive);
		panel.showActiveFlips(response);
		panel.toggleFlip(42);
		JLabel notFlip = findLabel(panel, "Not a flip");
		assertNotNull(notFlip);

		MouseEvent click = new MouseEvent(notFlip, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
		for (MouseListener listener : notFlip.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		assertEquals(42L, captured[0]);
	}

	private static JLabel findLabel(Component component, String text) {
		if (component instanceof JLabel && text.equals(((JLabel) component).getText())) {
			return (JLabel) component;
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				JLabel found = findLabel(child, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return false;
		}
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static BufferedImage paint(FlipsPanel panel) {
		panel.setSize(PANEL_WIDTH, Math.max(panel.getPreferredSize().height, 400));
		layoutTree(panel);
		panel.setSize(PANEL_WIDTH, Math.max(panel.getPreferredSize().height, 400));
		layoutTree(panel);
		BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		panel.paint(g2);
		g2.dispose();
		return image;
	}

	private static void layoutTree(Component component) {
		component.doLayout();
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				layoutTree(child);
			}
		}
	}

	private static void write(BufferedImage image, String name) throws Exception {
		File out = new File("build/preview/" + name);
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	private static int countDistinctColors(BufferedImage image) {
		java.util.Set<Integer> colors = new java.util.HashSet<>();
		for (int y = 0; y < image.getHeight(); y += 7) {
			for (int x = 0; x < image.getWidth(); x += 7) {
				colors.add(image.getRGB(x, y));
			}
		}
		return colors.size();
	}
}
