package app.geuncut.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;

import app.geuncut.dto.FlipsResponse;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Offscreen render harness: builds the real panel from Gson-parsed fixture JSON
 * (the exact shape the API serves), paints it headlessly, and writes PNGs to
 * build/preview/ for eyeballing layout changes before they ship. Also a smoke
 * test that the panel constructs and paints without a client.
 */
public class FlipsPanelRenderTest {
	private static final int PANEL_WIDTH = 242;

	private static final String FLIPS_JSON = "{\"flips\":[" +
			"{\"item_id\":1623,\"name\":\"Uncut diamond\",\"buy_price\":2512,\"target_sell_price\":2617," +
			"\"quantity\":10000,\"total_profit\":530000,\"profit_per_item\":53,\"position_cost\":25120000," +
			"\"roi\":2.1,\"roi_per_day\":0.7,\"entry_discount_pct\":4.0,\"strategy\":\"value\",\"members\":false," +
			"\"buy_fill\":{\"worst_case_hours\":0.23},\"sell_fill\":{\"worst_case_hours\":0.53}}," +
			"{\"item_id\":383,\"name\":\"Marlin\",\"buy_price\":3522,\"target_sell_price\":3927," +
			"\"quantity\":28392,\"total_profit\":9280000,\"profit_per_item\":327,\"position_cost\":100000000," +
			"\"roi\":9.3,\"roi_per_day\":3.1,\"entry_discount_pct\":10.3,\"strategy\":\"standard\",\"members\":true," +
			"\"horizon\":{\"recommended_days\":3}," +
			"\"demand_trend\":{\"direction\":\"RISING\"}," +
			"\"buy_fill\":{\"worst_case_hours\":108.0},\"sell_fill\":{\"worst_case_hours\":74.4}}" +
			"]}";

	/** Icon loader that never touches RuneLite: a flat placeholder sprite. */
	private static ItemIconLoader stubIcons() {
		return new ItemIconLoader(null, null) {
			@Override
			Image sprite(int itemId, Runnable onLoaded) {
				return new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			}

			@Override
			void load(int itemId, String name, javax.swing.JLabel label, int size) {
				// No sprite service in tests; the label simply stays iconless.
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

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem);
		panel.showFlips(response.getFlips(), false);
		BufferedImage unlinked = paint(panel);
		write(unlinked, "panel-unlinked.png");

		panel.showFlips(response.getFlips(), true);
		write(paint(panel), "panel-linked.png");

		// Not a blank canvas: something actually painted beyond the background.
		assertTrue(countDistinctColors(unlinked) > 8);
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
