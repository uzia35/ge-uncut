package app.geuncut.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import app.geuncut.dto.ItemHistory;
import app.geuncut.dto.ItemHistory.Candle;
import app.geuncut.ui.ItemChartData.Range;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemChartDataTest {
	private static long instant(String value) { return Instant.parse(value).toEpochMilli(); }

	@Test
	public void localIntradayDatesRollOverWithoutChangingTheRecordedInstant() {
		long timestamp = instant("2026-10-23T00:05:00Z");
		ItemHistory history = new ItemHistory(Collections.singletonList(
				new Candle("2026-10-23T00:05:00", null, 100L, 101L)), null);
		ItemChartData data = ItemChartData.from(history, Range.DAY);
		assertEquals("22 Oct 2026 20:05 EDT (-04:00)",
				ItemHistoryChart.hoverTime(Range.DAY, timestamp, ZoneId.of("America/New_York")));
		assertEquals("23 Oct 2026 09:05 JST (+09:00)",
				ItemHistoryChart.hoverTime(Range.DAY, timestamp, ZoneId.of("Asia/Tokyo")));
		assertEquals("23 Oct 2026 08:55 JST (+09:00)", ItemHistoryChart.hoverTime(Range.DAY,
				instant("2026-10-22T23:55:00Z"), ZoneId.of("Asia/Tokyo")));
		assertEquals(timestamp, data.getPoints().get(0).getTime());
		assertEquals(86_400_000L, data.getEnd() - data.getStart());
	}

	@Test
	public void springDstSkipsTheMissingLocalHourOnBothAxesAndHover() {
		ZoneId zone = ZoneId.of("America/New_York");
		long before = instant("2026-03-08T06:55:00Z");
		long after = instant("2026-03-08T07:00:00Z");
		assertEquals("01:55 EST", ItemHistoryChart.axisTime(Range.DAY, before, zone));
		assertEquals("03:00 EDT", ItemHistoryChart.axisTime(Range.DAY, after, zone));
		assertEquals("08 Mar 2026 01:55 EST (-05:00)", ItemHistoryChart.hoverTime(Range.DAY, before, zone));
		assertEquals("08 Mar 2026 03:00 EDT (-04:00)", ItemHistoryChart.hoverTime(Range.DAY, after, zone));
	}

	@Test
	public void autumnDstRepeatedHourHasDistinctZonesAndOffsets() {
		ZoneId zone = ZoneId.of("America/New_York");
		long first = instant("2026-11-01T05:30:00Z");
		long second = instant("2026-11-01T06:30:00Z");
		assertEquals("01:30 EDT", ItemHistoryChart.axisTime(Range.DAY, first, zone));
		assertEquals("01:30 EST", ItemHistoryChart.axisTime(Range.DAY, second, zone));
		assertEquals("01 Nov 2026 01:30 EDT (-04:00)", ItemHistoryChart.hoverTime(Range.DAY, first, zone));
		assertEquals("01 Nov 2026 01:30 EST (-05:00)", ItemHistoryChart.hoverTime(Range.DAY, second, zone));
	}

	@Test
	public void fractionalOffsetsAreShownWithoutRounding() {
		String label = ItemHistoryChart.hoverTime(Range.DAY, instant("2026-10-23T00:05:00Z"), ZoneId.of("Asia/Kathmandu"));
		assertTrue(label.startsWith("23 Oct 2026 05:50 "));
		assertTrue(label.endsWith("(+05:45)"));
	}

	@Test
	public void allDailyRangesKeepTheSourceDayAcrossLocalZonesAndYearRollover() {
		long timestamp = instant("2026-01-01T00:00:00Z");
		for (Range range : new Range[] {Range.WEEK, Range.MONTH, Range.YEAR}) {
			for (String id : new String[] {"America/Los_Angeles", "Pacific/Kiritimati", "America/New_York"}) {
				assertEquals("01 Jan 2026", ItemHistoryChart.hoverTime(range, timestamp, ZoneId.of(id)));
				assertEquals(range == Range.YEAR ? "Jan 26" : "01 Jan", ItemHistoryChart.axisTime(range, timestamp, ZoneId.of(id)));
			}
		}
	}

	@Test
	public void sparseWeekUsesCalendarWindowAndDoesNotInventObservations() {
		ItemHistory history = new ItemHistory(Collections.emptyList(), Arrays.asList(
				new Candle(null, "2026-10-20", 3_000_000_000L, null),
				new Candle(null, "2026-08-01", 2L, 3L),
				new Candle(null, "2026-10-13", 2_900_000_000L, 3_100_000_000L)));
		ItemChartData data = ItemChartData.from(history, Range.WEEK);
		assertEquals(2, data.getPoints().size());
		assertEquals(Instant.parse("2026-10-13T00:00:00Z").toEpochMilli(), data.getStart());
		assertEquals(3_100_000_000d, data.getMax(), 0);
		assertNull(data.getPoints().get(1).getSell());
	}

	@Test
	public void naiveAndOffsetTimestampsAreUtcAndInvalidPricesAreNotZeroes() {
		ItemHistory history = new ItemHistory(Arrays.asList(
				new Candle("2026-10-22T00:05:00", null, 100L, 101L),
				new Candle("2026-10-22T01:00:00+01:00", null, null, 101L),
				new Candle("invalid", null, 99L, 102L),
				new Candle("2026-10-22T00:10:00Z", null, 0L, -1L),
				null), Collections.emptyList());
		ItemChartData data = ItemChartData.from(history, Range.DAY);
		assertEquals(2, data.getPoints().size());
		assertEquals(Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(), data.getPoints().get(0).getTime());
		assertEquals(100d, data.getMin(), 0);
	}

	@Test
	public void emptyAndSingleFlatPointHaveFiniteAxes() {
		assertTrue(ItemChartData.from(new ItemHistory(null, null), Range.MONTH).getPoints().isEmpty());
		ItemHistory history = new ItemHistory(null, Collections.singletonList(new Candle(null, "2026-10-22", 42L, 42L)));
		ItemChartData data = ItemChartData.from(history, Range.MONTH);
		assertEquals(1, data.getPoints().size());
		assertTrue(data.getMax() > data.getMin());
		assertEquals("3.00B", ItemHistoryChart.compact(3_000_000_000d));
	}

	@Test
	public void volumeKeepsZeroUnknownAndOverflowDistinctWithoutInventingPrices() {
		ItemHistory history = new ItemHistory(null, Arrays.asList(
				new Candle(null, "2026-10-20", null, null, 0L, 0L),
				new Candle(null, "2026-10-21", null, null, 4_000_000_000L, -1L),
				new Candle(null, "2026-10-22", null, null, Long.MAX_VALUE, 1L)));
		ItemChartData data = ItemChartData.from(history, Range.MONTH);
		assertEquals(3, data.getPoints().size());
		assertFalse(data.hasPrices());
		assertTrue(data.getMax() > data.getMin());
		assertEquals(Long.valueOf(0), data.getPoints().get(0).totalVolume());
		assertNull(data.getPoints().get(1).getSellVolume());
		assertNull(data.getPoints().get(1).totalVolume());
		assertNull(data.getPoints().get(2).totalVolume());
		assertNull(data.changePercent(true));
	}

	@Test
	public void changeUsesObservedRangeEndpointsAndHoverSnapsToRealBuckets() {
		ItemHistory history = new ItemHistory(null, Arrays.asList(
				new Candle(null, "2026-08-01", 1L, 1L),
				new Candle(null, "2026-10-20", 100L, null),
				new Candle(null, "2026-10-21", null, 200L),
				new Candle(null, "2026-10-22", 110L, null)));
		ItemChartData data = ItemChartData.from(history, Range.WEEK);
		assertEquals(10.0, data.changePercent(true), 0.001);
		assertNull(data.changePercent(false));
		assertEquals(data.getPoints().get(1), data.nearest(Instant.parse("2026-10-21T01:00:00Z").toEpochMilli()));
		assertEquals(data.getPoints().get(0), data.nearest(data.getStart()));
	}

	@Test
	public void priceTicksEncloseDataAndRemainDistinctForNarrowBillionGpRange() {
		for (double[] bounds : new double[][] {{1, 2}, {2393, 2496}, {3_000_000_000d, 3_000_000_002d}}) {
			double[] ticks = ItemHistoryChart.priceTicks(bounds[0], bounds[1]);
			assertTrue(ticks[0] <= bounds[0]);
			assertTrue(ticks[ticks.length - 1] >= bounds[1]);
			assertTrue(ticks.length >= 2 && ticks.length <= 7);
			String[] labels = ItemHistoryChart.tickLabels(ticks);
			for (int i = 1; i < labels.length; i++) { assertNotEquals(labels[i - 1], labels[i]); }
		}
	}

	@Test
	public void sharedPointValuesKeepBothPricesAndBothVolumesIncludingMissingAndZero() {
		ItemChartData.Point point = new ItemChartData.Point(0L, 3200L, null, 0L, null);
		assertArrayEquals(new String[] {"3,200 gp", "n/a gp", "0 items", "Not reported"}, ItemHistoryChart.hoverValues(point));
		point = new ItemChartData.Point(0L, Long.MAX_VALUE, 3286L, 3_000_000_000L, Long.MAX_VALUE);
		assertArrayEquals(new String[] {"9,223,372,036,854,775,807 gp", "3,286 gp", "3,000,000,000 items",
				"9,223,372,036,854,775,807 items"}, ItemHistoryChart.hoverValues(point));
	}

	@Test
	public void sharedTooltipIsBoundedInsideCompactAndExpandedChartsAtEitherEdge() {
		for (int[] size : new int[][] {{220, 154}, {304, 140}, {456, 200}}) {
			for (int x : new int[] {0, size[0] / 2, size[0]}) {
				java.awt.Rectangle card = ItemHistoryChart.hoverBounds(x, 35, 300, size[0], size[1]);
				assertTrue(card.x >= 0 && card.y >= 0);
				assertTrue(card.x + card.width + 2 < size[0]);
				assertTrue(card.y + card.height + 3 < size[1]);
			}
		}
	}

	@Test
	public void nativeNarrowTooltipFitsExactLongValuesAndLocalTimestamp() {
		java.awt.Graphics2D g = new java.awt.image.BufferedImage(500, 500,
				java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
		try {
			ItemHistoryChart.prepare(g);
			ItemChartData.Point point = new ItemChartData.Point(Instant.parse("2026-10-23T00:00:00Z").toEpochMilli(),
					Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
			String[] values = ItemHistoryChart.hoverValues(point);
			for (double scale : new double[] {1, 1.25}) {
				g.setTransform(java.awt.geom.AffineTransform.getScaleInstance(scale, scale));
				for (Range range : Range.values()) {
					String stamp = ItemHistoryChart.hoverTime(range, point.getTime(), java.time.ZoneId.of("Asia/Kathmandu"));
					String qualifier = range.intraday ? "5-minute averages" : "Daily averages";
					java.awt.Rectangle card = ItemHistoryChart.hoverBounds(100, 35, 500, 220, 154);
					ItemHistoryChart.fitHoverFont(g, stamp, qualifier, values, card.width);
					assertTrue(ItemHistoryChart.hoverContentWidth(g, stamp, qualifier, values) <= card.width);
					assertTrue(g.getFont().getSize() >= 9);
				}
			}
		} finally { g.dispose(); }
	}

	@Test
	public void compactHoverKeepsMostOfChartVisibleWithExactPriceAndVolume() {
		java.awt.Graphics2D g = new java.awt.image.BufferedImage(220, 154,
				java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
		try {
			ItemHistoryChart.prepare(g);
			long time = Instant.parse("2026-10-23T01:15:00Z").toEpochMilli();
			for (Range range : Range.values()) {
				String stamp = ItemHistoryChart.hoverTime(range, time, java.time.ZoneId.of("America/New_York"));
				String[] values = ItemHistoryChart.hoverValues(new ItemChartData.Point(time, 3200L, 3286L, 0L, null));
				int width = ItemHistoryChart.tableContentWidth(g, stamp, values);
				assertTrue("Normal exact values use two rows", width <= 217);
				java.awt.Rectangle price = ItemHistoryChart.hoverBounds(100, 40, width, 220, 154);
				java.awt.Rectangle volume = ItemHistoryChart.hoverBounds(100, 120, width, 220, 154);
				assertEquals(65, price.height);
				assertTrue(price.width * price.height < 220 * 154 / 2);
				assertTrue(price.y > 40);
				assertEquals(0, volume.y);
				assertFalse(volume.contains(100, 120));
				values = ItemHistoryChart.hoverValues(new ItemChartData.Point(time, Long.MAX_VALUE, Long.MAX_VALUE,
						Long.MAX_VALUE, Long.MAX_VALUE));
				assertTrue(ItemHistoryChart.tableContentWidth(g, stamp, values) > 217);
				java.awt.Rectangle fallback = ItemHistoryChart.hoverBounds(100, 40, 400, 220, 154, 73);
				assertTrue(fallback.height < 154 * 0.55);
				for (int mouseY = 34; mouseY <= 136; mouseY++) {
					assertFalse(ItemHistoryChart.hoverBounds(100, mouseY, 400, 220, 154, 73).contains(100, mouseY));
				}
				ItemHistoryChart.fitHoverFont(g, stamp, "", values, fallback.width);
				assertTrue(ItemHistoryChart.hoverContentWidth(g, stamp, "", values) <= fallback.width);
				g.setFont(ItemHistoryChart.FONT);
			}
		} finally { g.dispose(); }
	}

	@Test
	public void nativeNarrowTimeAxisChoosesNonoverlappingTicksForAllRanges() {
		java.awt.Graphics2D g = new java.awt.image.BufferedImage(220, 154,
				java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
		try {
			g.setFont(ItemHistoryChart.FONT.deriveFont(10f));
			long start = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();
			for (Range range : Range.values()) {
				for (String zone : new String[] {"America/New_York", "Asia/Kathmandu"}) {
					for (int left : new int[] {40, 105}) {
						java.awt.Rectangle plot = new java.awt.Rectangle(left, 34, 215 - left, 54);
						java.time.ZoneId local = java.time.ZoneId.of(zone);
						int count = ItemHistoryChart.timeTickCount(g, range, start, local, plot, 220);
						int previousRight = -1;
						for (int i = 0; i < count; i++) {
							long time = start + (count == 1 ? range.windowMs : range.windowMs * i / (count - 1));
							int textWidth = g.getFontMetrics().stringWidth(ItemHistoryChart.axisTime(range, time, local));
							int x = plot.x + (count == 1 ? plot.width : plot.width * i / (count - 1));
							int labelX = Math.max(plot.x, Math.min(220 - textWidth, x - textWidth / 2));
							assertTrue(previousRight < 0 || labelX >= previousRight + 6);
							assertTrue(labelX + textWidth <= 220);
							previousRight = labelX + textWidth;
						}
					}
				}
			}
		} finally { g.dispose(); }
	}

	@Test
	public void missingVolumePaintsNoBarWhileZeroPaintsOnlyTheBaseline() {
		for (Long volume : new Long[] {null, 0L, 10L}) {
			ItemHistory history = new ItemHistory(null, Collections.singletonList(
					new Candle(null, "2026-10-23", 100L, 101L, volume, null)));
			java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(304, 140, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D graphics = image.createGraphics();
			try {
				new ItemHistoryChart().render(graphics, new java.awt.Rectangle(0, 0, 304, 140),
						new app.geuncut.service.ItemHistoryService.Snapshot(536,
								app.geuncut.service.ItemHistoryService.Status.READY, history), Range.MONTH, null);
			} finally { graphics.dispose(); }
			int blueAboveBaseline = 0, blueOnBaseline = 0, green = 0;
			for (int y = 98; y <= 122; y++) {
				for (int x = 40; x < 304; x++) {
					int rgb = image.getRGB(x, y) & 0xffffff;
					if (rgb == (ItemHistoryChart.BUY.getRGB() & 0xffffff)) {
						if (y < 122) { blueAboveBaseline++; } else { blueOnBaseline++; }
					}
					if (rgb == (ItemHistoryChart.SELL.getRGB() & 0xffffff)) { green++; }
				}
			}
			assertEquals(0, green);
			if (volume == null) { assertEquals(0, blueAboveBaseline + blueOnBaseline); }
			else if (volume == 0) { assertEquals(0, blueAboveBaseline); assertTrue(blueOnBaseline > 0); }
			else { assertTrue(blueAboveBaseline > 0); }
		}
	}

	@Test
	public void volumeBarsKeepMissingZeroLargeValuesAndTradeSidesDistinct() {
		ItemHistory history = new ItemHistory(null, Arrays.asList(
				new Candle(null, "2026-10-21", 100L, 101L, 0L, null),
				new Candle(null, "2026-10-23", 100L, 101L, 3_000_000_000L, 4_000_000_000L)));
		ItemChartData data = ItemChartData.from(history, Range.MONTH);
		assertEquals(4_000_000_000L, ItemHistoryChart.maxVolume(data));
		assertEquals(-1, ItemHistoryChart.volumeHeight(null, 4_000_000_000L, 40));
		assertEquals(0, ItemHistoryChart.volumeHeight(0L, 4_000_000_000L, 40));
		assertEquals(30, ItemHistoryChart.volumeHeight(3_000_000_000L, 4_000_000_000L, 40));
		assertEquals(40, ItemHistoryChart.volumeHeight(Long.MAX_VALUE, Long.MAX_VALUE, 40));
		assertEquals(2, data.getPoints().size());
	}
}
