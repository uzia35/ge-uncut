package app.geuncut.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import app.geuncut.dto.ItemHistory;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import lombok.Value;

/** Calendar windows anchored to the newest observation, like the web item chart. Never fills gaps. */
@Value
public class ItemChartData {
	List<Point> points;
	long start;
	long end;
	double min;
	double max;

	public enum Range {
		DAY("24H", 1, true), WEEK("1W", 7, false), MONTH("1M", 30, false), YEAR("1Y", 365, false);
		public final String label;
		public final long windowMs;
		public final long bucketMs;
		public final boolean intraday;
		Range(String label, int days, boolean intraday) {
			this.label = label;
			this.windowMs = days * 86_400_000L;
			this.bucketMs = intraday ? 300_000L : 86_400_000L;
			this.intraday = intraday;
		}
	}

	@Value
	public static class Point {
		long time;
		Long buy;
		Long sell;
		Long buyVolume;
		Long sellVolume;

		/** Unknown on either side is not evidence of zero volume. */
		public Long totalVolume() {
			if (buyVolume == null || sellVolume == null) { return null; }
			try { return Math.addExact(buyVolume, sellVolume); }
			catch (ArithmeticException overflow) { return null; }
		}
	}

	static Point latest(Snapshot snapshot, Range range) {
		if (snapshot == null || snapshot.getStatus() != Status.READY || snapshot.getHistory() == null) { return null; }
		ItemChartData data = from(snapshot.getHistory(), range);
		return data.points.isEmpty() ? null : data.points.get(data.points.size() - 1);
	}

	static String quantity(Long value) {
		return value == null ? "Not reported" : String.format(java.util.Locale.ENGLISH, "%,d items", value);
	}

	public Point nearest(long time) {
		Point nearest = null;
		for (Point point : points) {
			if (nearest == null || Math.abs(point.time - time) < Math.abs(nearest.time - time)) { nearest = point; }
		}
		return nearest;
	}

	/** First-to-last observed average within the range, excluding absent sides. */
	public Double changePercent(boolean buy) {
		Point first = null;
		Point last = null;
		for (Point point : points) {
			if ((buy ? point.buy : point.sell) != null) {
				if (first == null) { first = point; }
				last = point;
			}
		}
		if (first == null || last.time <= first.time) { return null; }
		long initial = buy ? first.buy : first.sell;
		long latest = buy ? last.buy : last.sell;
		return (latest - initial) * 100.0 / initial;
	}

	public boolean hasPrices() {
		return points.stream().anyMatch(point -> point.buy != null || point.sell != null);
	}

	public static ItemChartData from(ItemHistory history, Range range) {
		List<Point> sorted = new ArrayList<>();
		List<ItemHistory.Candle> candles = range.intraday ? history.getIntraday() : history.getDaily();
		if (candles != null) {
			for (ItemHistory.Candle candle : candles) {
				if (candle == null) { continue; }
				Long timestamp = timestamp(range.intraday ? candle.getBucketTs() : candle.getDay(), range.intraday);
				Long buy = positive(candle.getBuy());
				Long sell = positive(candle.getSell());
				Long buyVolume = nonNegative(candle.getBuyVolume());
				Long sellVolume = nonNegative(candle.getSellVolume());
				if (timestamp != null && (buy != null || sell != null || buyVolume != null || sellVolume != null)) {
					sorted.add(new Point(timestamp, buy, sell, buyVolume, sellVolume));
				}
			}
		}
		sorted.sort(Comparator.comparingLong(Point::getTime));
		long end = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1).time;
		long start = end - range.windowMs;
		sorted.removeIf(point -> point.time < start);
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Point point : sorted) {
			if (point.buy != null) { min = Math.min(min, point.buy); max = Math.max(max, point.buy); }
			if (point.sell != null) { min = Math.min(min, point.sell); max = Math.max(max, point.sell); }
		}
		if (!Double.isFinite(min)) { min = 0; max = 1; }
		if (min == max) {
			double padding = Math.max(1, Math.ulp(min) * 2);
			min = Math.max(0, min - padding); max += padding;
		}
		return new ItemChartData(Collections.unmodifiableList(sorted), start, end, min, max);
	}

	private static Long positive(Long value) {
		return value != null && value > 0 ? value : null;
	}

	private static Long nonNegative(Long value) {
		return value != null && value >= 0 ? value : null;
	}

	private static Long timestamp(String value, boolean intraday) {
		if (value == null) { return null; }
		try {
			if (!intraday) { return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(); }
			// The API emits naive UTC timestamps; it may also include an explicit offset.
			TemporalAccessor parsed = java.time.format.DateTimeFormatter.ISO_DATE_TIME.parseBest(value,
					Instant::from, LocalDateTime::from);
			return parsed instanceof Instant ? ((Instant) parsed).toEpochMilli()
					: ((LocalDateTime) parsed).toInstant(ZoneOffset.UTC).toEpochMilli();
		} catch (DateTimeParseException exception) {
			return null;
		}
	}
}
