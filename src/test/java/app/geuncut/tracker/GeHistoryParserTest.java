package app.geuncut.tracker;

import java.util.List;

import app.geuncut.dto.GeHistoryRow;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeHistoryParserTest {
	private static Widget text(String value) {
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(value);
		when(widget.getItemId()).thenReturn(-1);
		return widget;
	}

	private static Widget item(int itemId, int quantity) {
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn("");
		when(widget.getItemId()).thenReturn(itemId);
		when(widget.getItemQuantity()).thenReturn(quantity);
		return widget;
	}

	private static Widget container(Widget... children) {
		Widget widget = mock(Widget.class);
		when(widget.getDynamicChildren()).thenReturn(children);
		return widget;
	}

	@Test
	public void parsesSoldAndBoughtRowsWithTaxBreakdown() {
		Widget container = container(
				text("<col=ff981f>Sold:</col>"),
				text("Superior dragon bones<br>x 5,000"),
				item(22124, 5000),
				text("105,350,000 coins (107,500,000 - 2,150,000) = 21,070 each"),
				text("Bought:"),
				text("Superior dragon bones<br>x 2,908"),
				item(22124, 2908),
				text("60,643,432 coins = 20,854 each"));

		List<GeHistoryRow> rows = GeHistoryParser.parse(container);

		assertEquals(2, rows.size());
		assertEquals("sell", rows.get(0).getSide());
		assertEquals(22124, rows.get(0).getItemId());
		assertEquals(5000, rows.get(0).getQuantity());
		assertEquals(21070, rows.get(0).getPriceEach());
		assertEquals("buy", rows.get(1).getSide());
		assertEquals(2908, rows.get(1).getQuantity());
		assertEquals(20854, rows.get(1).getPriceEach());
	}

	@Test
	public void singleUnitRowFallsBackToCoinsTotal() {
		Widget container = container(
				text("Bought:"),
				text("Twisted bow<br>x 1"),
				item(20997, 1),
				text("1,400,000,000 coins"));

		List<GeHistoryRow> rows = GeHistoryParser.parse(container);

		assertEquals(1, rows.size());
		assertEquals("buy", rows.get(0).getSide());
		assertEquals(1, rows.get(0).getQuantity());
		assertEquals(1_400_000_000, rows.get(0).getPriceEach());
	}

	@Test
	public void headerWithoutItemOrPriceIsSkipped() {
		Widget container = container(
				text("Sold:"),
				text("Superior dragon bones<br>x 5,000"),
				text("Bought:"),
				text("Shark<br>x 100"),
				item(385, 100),
				text("80,000 coins = 800 each"));

		List<GeHistoryRow> rows = GeHistoryParser.parse(container);

		assertEquals(1, rows.size());
		assertEquals("buy", rows.get(0).getSide());
		assertEquals(385, rows.get(0).getItemId());
	}

	@Test
	public void colorTagDigitsNeverPolluteNumbers() {
		Widget container = container(
				text("Sold:"),
				text("Cannonball<br>x 2,000"),
				item(2, 2000),
				text("<col=ffb83f>400,000 coins (408,163 - 8,163) = 200 each</col>"));

		List<GeHistoryRow> rows = GeHistoryParser.parse(container);

		assertEquals(1, rows.size());
		assertEquals(200, rows.get(0).getPriceEach());
	}

	@Test
	public void nullOrEmptyContainerParsesToNothing() {
		assertTrue(GeHistoryParser.parse(null).isEmpty());
		assertTrue(GeHistoryParser.parse(container()).isEmpty());
	}
}
