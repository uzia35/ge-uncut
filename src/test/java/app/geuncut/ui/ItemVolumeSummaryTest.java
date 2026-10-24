package app.geuncut.ui;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import app.geuncut.dto.ItemHistory;
import app.geuncut.dto.ItemHistory.Candle;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.ItemChartData.Range;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemVolumeSummaryTest {
	@Test
	public void summaryTimesRetainOriginalRecordedQuoteAges() {
		app.geuncut.dto.ItemPrice price = new app.geuncut.dto.ItemPrice(536, 3200L, 3300L,
				"2026-10-23T01:15:00", "2026-10-23T01:19:00Z");
		Instant now = Instant.parse("2026-10-23T01:20:00Z");
		assertEquals("5m ago", OfferPriceOverlay.quoteAge(price, true, now));
		assertEquals("1m ago", OfferPriceOverlay.quoteAge(price, false, now));
		assertEquals("Time unknown", OfferPriceOverlay.quoteAge(null, true, now));
		assertEquals("Time unknown", OfferPriceOverlay.quoteAge(
				new app.geuncut.dto.ItemPrice(536, 3200L, 3300L, null, "bad"), false, now));
	}

	@Test
	public void topSummaryRetainsBothSidesAndMissingStates() {
		long time = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli();
		ItemChartData.Point point = new ItemChartData.Point(time, 100L, 101L, 0L, null);
		assertArrayEquals(new String[] {"0 items", "Not reported"}, OfferPriceOverlay.topVolumeValues(point, Status.READY));
		assertArrayEquals(new String[] {"Loading", "Loading"}, OfferPriceOverlay.topVolumeValues(point, Status.LOADING));
		assertArrayEquals(new String[] {"Unavailable", "Unavailable"}, OfferPriceOverlay.topVolumeValues(point, Status.ERROR));

	}

	@Test
	public void summaryKeepsExactPricesAgesAndLargeVolumeCounts() {
		Instant now = Instant.parse("2026-10-23T01:20:00Z");
		app.geuncut.dto.ItemPrice price = new app.geuncut.dto.ItemPrice(536, 3_000_000_000L, 3300L,
				"2026-10-23T01:15:00", "2026-10-23T01:19:00Z");
		assertEquals("3,000,000,000 gp   5m ago", OfferPriceOverlay.summaryQuote(price, true, now));
		ItemChartData.Point point = new ItemChartData.Point(now.toEpochMilli(), 1L, 2L, Long.MAX_VALUE, 0L);
		assertArrayEquals(new String[] {"9,223,372,036,854,775,807 items", "0 items"},
				OfferPriceOverlay.topVolumeValues(point, Status.READY));
	}

	@Test
	public void reportedApiSidesMatchPricesWithoutCombiningVolumes() {
		ItemHistory history = new Gson().fromJson("{\"intraday_candles\":[{\"bucket_ts\":\"2026-10-23T01:15:00\","
				+ "\"avg_low_price\":3200,\"avg_high_price\":3300,\"low_price_volume\":12,\"high_price_volume\":34}]}", ItemHistory.class);
		ItemChartData.Point point = ItemChartData.latest(new Snapshot(536, Status.READY, history), Range.DAY);
		assertEquals(Long.valueOf(12), point.getBuyVolume());
		assertEquals(Long.valueOf(34), point.getSellVolume());
		assertEquals("12 items", ItemChartData.quantity(point.getBuyVolume()));
		assertEquals("34 items", ItemChartData.quantity(point.getSellVolume()));
		assertArrayEquals(new String[] {"12 items", "34 items"}, OfferPriceOverlay.topVolumeValues(point, Status.READY));
	}

	@Test
	public void allRangesShowTheirLatestBucketRatherThanAddingSparseHistory() {
		ItemHistory history = new ItemHistory(Arrays.asList(
				new Candle("2026-10-23T01:15:00", null, 10L, 11L, 21L, 22L),
				new Candle("2026-10-22T01:15:00", null, 10L, 11L, 500L, 600L)), Arrays.asList(
				new Candle(null, "2026-10-21", 10L, 11L, 1000L, 2000L),
				new Candle(null, "2026-10-23", 10L, 11L, 31L, 32L)));
		Snapshot snapshot = new Snapshot(536, Status.READY, history);
		for (Range range : Range.values()) {
			ItemChartData.Point point = ItemChartData.latest(snapshot, range);
			assertEquals(Long.valueOf(range.intraday ? 21 : 31), point.getBuyVolume());
			assertEquals(Long.valueOf(range.intraday ? 22 : 32), point.getSellVolume());
		}
	}

	@Test
	public void zeroUnknownAndInvalidVolumeStayDistinctEvenWithoutPrices() {
		for (Long missing : new Long[] {null, -1L}) {
			ItemHistory history = new ItemHistory(null, Collections.singletonList(
					new Candle(null, "2026-10-23", null, null, 0L, missing)));
			ItemChartData.Point point = ItemChartData.latest(new Snapshot(536, Status.READY, history), Range.MONTH);
			assertEquals("0 items", ItemChartData.quantity(point.getBuyVolume()));
			assertEquals("Not reported", ItemChartData.quantity(point.getSellVolume()));
		}
		assertEquals("9,223,372,036,854,775,807 items", ItemChartData.quantity(Long.MAX_VALUE));
	}

	@Test
	public void absenceAndNonReadyStatesNeverShowPreviousVolume() {
		ItemHistory empty = new ItemHistory(null, null);
		assertNull(ItemChartData.latest(null, Range.DAY));
		for (Status status : Status.values()) {
			assertNull(ItemChartData.latest(new Snapshot(536, status, empty), Range.MONTH));
		}
	}

}
