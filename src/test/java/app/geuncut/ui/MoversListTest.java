package app.geuncut.ui;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.geuncut.dto.MoverEntry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MoversListTest {
	private static final int ROW_HEIGHT = 36;
	private static final int WIDTH = 200;

	@Test
	public void everyEntryIsDrawnAtOnce() {
		MoversList list = new MoversList(Theme.NUM_SMALL, Theme.UP, (itemId, onLoaded) -> null, itemId -> {
		});
		list.setEntries(entries(10));
		assertEquals(10 * ROW_HEIGHT, list.getPreferredSize().height);

		BufferedImage image = paint(list);
		for (int row = 0; row < 10; row++) {
			assertTrue("row " + (row + 1) + " is blank", inked(image, row * ROW_HEIGHT, list.getBackground().getRGB()));
		}

		list.setEntries(entries(3));
		assertEquals(3 * ROW_HEIGHT, list.getPreferredSize().height);
	}

	@Test
	public void clickingAnyRowOpensThatItem() {
		List<Integer> opened = new ArrayList<>();
		MoversList list = new MoversList(Theme.NUM_SMALL, Theme.UP, (itemId, onLoaded) -> null, opened::add);
		list.setEntries(entries(10));
		list.setSize(WIDTH, list.getPreferredSize().height);
		for (int row = 0; row < 10; row++) {
			click(list, row * ROW_HEIGHT + ROW_HEIGHT / 2);
		}
		click(list, 10 * ROW_HEIGHT + 1);
		assertEquals(Arrays.asList(100, 101, 102, 103, 104, 105, 106, 107, 108, 109), opened);
	}

	private static List<MoverEntry> entries(int count) {
		List<MoverEntry> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			entries.add(new MoverEntry(100 + i, "Item " + (i + 1), 20.0 - i, 1_000L * (i + 1), 50_000L, null));
		}
		return entries;
	}

	private static BufferedImage paint(MoversList list) {
		list.setSize(WIDTH, list.getPreferredSize().height);
		BufferedImage image = new BufferedImage(list.getWidth(), list.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		list.paint(g2);
		g2.dispose();
		return image;
	}

	private static boolean inked(BufferedImage image, int top, int background) {
		for (int y = top; y < top + ROW_HEIGHT; y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) != background) {
					return true;
				}
			}
		}
		return false;
	}

	private static void click(MoversList list, int y) {
		MouseEvent event = new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0L, 0, WIDTH / 2, y, 1, false);
		for (MouseListener listener : list.getMouseListeners()) {
			listener.mouseClicked(event);
		}
	}
}
