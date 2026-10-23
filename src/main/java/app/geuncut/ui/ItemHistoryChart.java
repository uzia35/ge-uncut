package app.geuncut.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.ItemChartData.Range;

/** Intraday labels use the player's zone; daily dates retain their source UTC-day meaning. */
final class ItemHistoryChart {
	static final int HEIGHT = 144;
	static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	// GEUncut dark-theme tokens; colour carries the same Buy/low and Sell/high meaning.
	static final Color SURFACE = new Color(0x141416);
	static final Color RAISED = new Color(0x1A1A1D);
	static final Color SELECTED = new Color(0x36363C);
	static final Color BORDER = new Color(0x26262A);
	static final Color INK = new Color(0xEDEDF0);
	static final Color MUTED = new Color(0x9A9AA4);
	static final Color BUY = new Color(0x4D9FFF);
	static final Color SELL = new Color(0x2EC27E);
	private static final Color AMBER = new Color(0xE8A13C);

	static void prepare(Graphics2D g) {
		g.setFont(FONT);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	void render(Graphics2D target, Rectangle area, Snapshot snapshot, Range range, Point mouse) {
		Graphics2D g = (Graphics2D) target.create();
		try {
			g.translate(area.x, area.y);
			prepare(g);
			if (snapshot == null || snapshot.getStatus() == Status.IDLE || snapshot.getStatus() == Status.LOADING) {
				message(g, "Loading price history.", area.width); return;
			}
			if (snapshot.getStatus() == Status.ERROR) {
				message(g, "Price history unavailable", area.width); return;
			}
			ItemChartData data = ItemChartData.from(snapshot.getHistory(), range);
			if (data.getPoints().isEmpty()) { message(g, "No " + range.label + " history", area.width); return; }
			boolean small = area.width < 400;
			ZoneId localZone = ZoneId.systemDefault();
			boolean stale = Instant.now().toEpochMilli() - data.getEnd() > (range.intraday ? 3_600_000 : 2 * 86_400_000L);
			g.setFont(FONT.deriveFont(10f));
			label(g, (stale ? "Older data · " : "") + (range.intraday ? "5-minute averages" : "Daily averages"),
					0, 10, stale ? AMBER : MUTED);
			g.setFont(FONT);
			legend(g, "Buy", 0, BUY);
			legend(g, "Sell", 60, SELL);
			g.setFont(FONT.deriveFont(10f));
			double[] ticks = priceTicks(data.getMin(), data.getMax());
			String[] labels = tickLabels(ticks);
			long maximum = maxVolume(data);
			int left = Math.max(40, g.getFontMetrics().stringWidth(compact(maximum)) + 7);
			for (String value : labels) { left = Math.max(left, g.getFontMetrics().stringWidth(value) + 7); }
			int volumeHeight = small ? 24 : 40;
			Rectangle volumePlot = new Rectangle(left, area.height - 18 - volumeHeight, area.width - left - 5, volumeHeight);
			Rectangle pricePlot = new Rectangle(left, 34, volumePlot.width, volumePlot.y - 22 - 34);
			if (data.hasPrices()) {
				for (int i = 0; i < ticks.length; i++) {
					if (small && i != 0 && i != ticks.length / 2 && i != ticks.length - 1) { continue; }
					int y = pricePlot.y + pricePlot.height - i * pricePlot.height / (ticks.length - 1);
					g.setColor(BORDER); g.drawLine(pricePlot.x, y, pricePlot.x + pricePlot.width, y);
					g.setColor(MUTED); right(g, labels[i], pricePlot.x - 7, y + 3);
				}
				drawSeries(g, data, range, pricePlot, ticks, true);
				drawSeries(g, data, range, pricePlot, ticks, false);
			} else { label(g, "No price observations", pricePlot.x + 8, pricePlot.y + 22, MUTED); }
			label(g, "Volume", pricePlot.x, volumePlot.y - 6, MUTED);
			if (maximum > 0) { g.setColor(MUTED); right(g, compact(maximum), volumePlot.x - 7, volumePlot.y + 4); }
			g.setColor(MUTED); right(g, "0", volumePlot.x - 7, volumePlot.y + volumePlot.height + 3);
			drawVolumes(g, data, range, volumePlot, maximum);
			g.setColor(MUTED);
			int timeTicks = timeTickCount(g, range, data.getStart(), localZone, pricePlot, area.width);
			for (int i = 0; i < timeTicks; i++) {
				long time = data.getStart() + (timeTicks == 1 ? range.windowMs : range.windowMs * i / (timeTicks - 1));
				String value = axisTime(range, time, localZone);
				int textWidth = g.getFontMetrics().stringWidth(value);
				int labelX = timeLabelX(x(time, data, pricePlot), textWidth, pricePlot, area.width);
				g.drawString(value, labelX, area.height - 2);
			}
			Point local = mouse == null ? null : new Point(mouse.x - area.x, mouse.y - area.y);
			Rectangle hoverArea = new Rectangle(pricePlot.x, pricePlot.y, pricePlot.width + 1, volumePlot.y + volumePlot.height - pricePlot.y + 1);
			if (local != null && hoverArea.contains(local)) {
				long time = data.getStart() + Math.round((local.x - pricePlot.x) * (double) range.windowMs / pricePlot.width);
				ItemChartData.Point selected = data.nearest(time);
				int selectedX = x(selected.getTime(), data, pricePlot);
				g.setStroke(new BasicStroke(1f)); g.setColor(MUTED);
				g.drawLine(selectedX, pricePlot.y, selectedX, volumePlot.y + volumePlot.height);
				marker(g, selectedX, selected.getBuy(), pricePlot, ticks, BUY);
				marker(g, selectedX, selected.getSell(), pricePlot, ticks, SELL);
				g.setFont(FONT);
				hoverCard(g, selected, range, localZone, selectedX, pricePlot.y + 1, area.width, area.height);
			}
		} finally { g.dispose(); }
	}

	static long maxVolume(ItemChartData data) {
		long maximum = 0;
		for (ItemChartData.Point point : data.getPoints()) {
			if (point.getBuyVolume() != null) { maximum = Math.max(maximum, point.getBuyVolume()); }
			if (point.getSellVolume() != null) { maximum = Math.max(maximum, point.getSellVolume()); }
		}
		return maximum;
	}

	/** Unknown has no mark; a reported zero is a baseline mark, never a positive bar. */
	static int volumeHeight(Long quantity, long maximum, int height) {
		if (quantity == null) { return -1; }
		if (quantity == 0) { return 0; }
		return Math.max(1, (int) Math.round(quantity / (double) Math.max(1L, maximum) * height));
	}

	private static void drawVolumes(Graphics2D g, ItemChartData data, Range range, Rectangle plot, long maximum) {
		g.setStroke(new BasicStroke(1f));
		double width = Math.min(12, Math.max(0.6, plot.width * (double) range.bucketMs / range.windowMs * 0.8));
		boolean any = false;
		for (ItemChartData.Point point : data.getPoints()) {
			double center = Math.max(plot.x + width / 2, Math.min(plot.x + plot.width - width / 2, x(point.getTime(), data, plot)));
			for (boolean buy : new boolean[] {true, false}) {
				int height = volumeHeight(buy ? point.getBuyVolume() : point.getSellVolume(), maximum, plot.height);
				if (height < 0) { continue; }
				any = true;
				g.setColor(buy ? BUY : SELL);
				double left = center + (buy ? -width / 2 : 0);
				if (height == 0) {
					g.draw(new java.awt.geom.Line2D.Double(left, plot.y + plot.height, left + width / 2, plot.y + plot.height));
				} else {
					g.fill(new java.awt.geom.Rectangle2D.Double(left, plot.y + plot.height - height, width / 2, height));
				}
			}
		}
		if (!any) { label(g, "Not reported", plot.x + 8, plot.y + plot.height - 3, MUTED); }
	}

	static String[] hoverValues(ItemChartData.Point point) {
		return new String[] {exact(point.getBuy()) + " gp", exact(point.getSell()) + " gp",
				ItemVolumeDetails.quantity(point.getBuyVolume()), ItemVolumeDetails.quantity(point.getSellVolume())};
	}

	static int timeTickCount(Graphics2D g, Range range, long start, ZoneId zone, Rectangle plot, int width) {
		for (int count = width < 400 ? 3 : 4; count > 1; count--) {
			int previousRight = -1;
			boolean fits = true;
			for (int i = 0; i < count; i++) {
				int textWidth = g.getFontMetrics().stringWidth(axisTime(range, start + range.windowMs * i / (count - 1), zone));
				int left = timeLabelX(plot.x + plot.width * i / (count - 1), textWidth, plot, width);
				if (previousRight >= 0 && left < previousRight + 6) { fits = false; break; }
				previousRight = left + textWidth;
			}
			if (fits) { return count; }
		}
		return 1;
	}

	private static int timeLabelX(int x, int textWidth, Rectangle plot, int width) {
		return Math.max(plot.x, Math.min(width - textWidth, x - textWidth / 2));
	}

	private static final String[] HOVER_LABELS = {"Buy price", "Sell price", "Buy volume", "Sell volume"};

	static int hoverContentWidth(Graphics2D g, String stamp, String qualifier, String[] values) {
		int width = Math.max(g.getFontMetrics().stringWidth(stamp), g.getFontMetrics().stringWidth(qualifier)) + 16;
		for (int i = 0; i < values.length; i++) {
			width = Math.max(width, g.getFontMetrics().stringWidth(values[i])
					+ g.getFontMetrics().stringWidth(HOVER_LABELS[i]) + 24);
		}
		return width;
	}

	static void fitHoverFont(Graphics2D g, String stamp, String qualifier, String[] values, int width) {
		g.setFont(FONT);
		while (hoverContentWidth(g, stamp, qualifier, values) > width && g.getFont().getSize() > 9) {
			g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - 1));
		}
	}

	static Rectangle hoverBounds(int selectedX, int requestedTop, int contentWidth, int width, int height) {
		int cardWidth = Math.min(width - 3, Math.max(244, contentWidth));
		int cardHeight = 126;
		int left = selectedX + 12 + cardWidth <= width - 3 ? selectedX + 12 : Math.max(0, selectedX - 12 - cardWidth);
		int top = Math.max(0, Math.min(requestedTop, height - cardHeight - 4));
		return new Rectangle(left, top, cardWidth, cardHeight);
	}

	private static void hoverCard(Graphics2D g, ItemChartData.Point point, Range range, ZoneId localZone,
			int selectedX, int requestedTop, int width, int height) {
		String stamp = hoverTime(range, point.getTime(), localZone);
		String qualifier = range.intraday ? "5-minute averages" : "Daily averages";
		String[] values = hoverValues(point);
		int contentWidth = hoverContentWidth(g, stamp, qualifier, values);
		Rectangle card = hoverBounds(selectedX, requestedTop, contentWidth, width, height);
		fitHoverFont(g, stamp, qualifier, values, card.width);
		g.setColor(new Color(0, 0, 0, 80));
		g.fillRoundRect(card.x + 2, card.y + 3, card.width, card.height, 10, 10);
		g.setColor(RAISED);
		g.fillRoundRect(card.x, card.y, card.width, card.height, 10, 10);
		g.setColor(SELECTED);
		g.drawRoundRect(card.x, card.y, card.width, card.height, 10, 10);
		label(g, stamp, card.x + 8, card.y + 17, INK);
		label(g, qualifier, card.x + 8, card.y + 33, MUTED);
		g.setColor(BORDER);
		g.drawLine(card.x + 12, card.y + 77, card.x + card.width - 12, card.y + 77);
		int[] baselines = {52, 69, 96, 113};
		for (int i = 0; i < values.length; i++) {
			label(g, HOVER_LABELS[i], card.x + 8, card.y + baselines[i], i % 2 == 0 ? BUY : SELL);
			g.setColor(INK);
			right(g, values[i], card.x + card.width - 8, card.y + baselines[i]);
		}
	}

	static void label(Graphics2D g, String text, int x, int baseline, Color color) {
		g.setColor(color); g.drawString(text, x, baseline);
	}

	static double[] priceTicks(double min, double max) {
		double rawStep = (max - min) / 4;
		double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(0.25, rawStep))));
		double scaled = rawStep / magnitude;
		double step = (scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10) * magnitude;
		step = Math.max(1, step);
		double lower = Math.max(0, Math.floor(min / step) * step);
		double upper = Math.ceil(max / step) * step;
		int intervals = Math.max(1, (int) Math.round((upper - lower) / step));
		double[] ticks = new double[intervals + 1];
		for (int i = 0; i < ticks.length; i++) { ticks[i] = lower + i * step; }
		return ticks;
	}

	static String[] tickLabels(double[] ticks) {
		String[] labels = new String[ticks.length];
		boolean duplicates = false;
		for (int i = 0; i < ticks.length; i++) {
			labels[i] = compact(ticks[i]);
			duplicates |= i > 0 && labels[i].equals(labels[i - 1]);
		}
		if (duplicates) {
			for (int i = 0; i < ticks.length; i++) { labels[i] = String.format(Locale.ENGLISH, "%,.0f", ticks[i]); }
		}
		return labels;
	}

	private static void drawSeries(Graphics2D g, ItemChartData data, Range range, Rectangle plot, double[] ticks, boolean buy) {
		g.setColor(buy ? BUY : SELL);
		g.setStroke(new BasicStroke(1.8f));
		ItemChartData.Point previous = null;
		Long lastValue = null;
		for (ItemChartData.Point point : data.getPoints()) {
			Long value = buy ? point.getBuy() : point.getSell();
			if (value != null) {
				int x = x(point.getTime(), data, plot);
				int y = y(value, plot, ticks);
				boolean connected = previous != null && lastValue != null && point.getTime() - previous.getTime() <= range.bucketMs;
				if (connected) { g.drawLine(x(previous.getTime(), data, plot), y(lastValue, plot, ticks), x, y); }
				// Visible observations, including a single isolated point; never fill gaps.
				g.fillOval(x - 1, y - 1, 2, 2);
			}
			previous = point;
			lastValue = value;
		}
	}

	private static void marker(Graphics2D g, int x, Long value, Rectangle plot, double[] ticks, Color color) {
		if (value != null) { g.setColor(color); g.fillOval(x - 3, y(value, plot, ticks) - 3, 6, 6); }
	}
	private static int x(long time, ItemChartData data, Rectangle plot) {
		return plot.x + (int) Math.round((time - data.getStart()) * (double) plot.width / (data.getEnd() - data.getStart()));
	}
	private static int y(long value, Rectangle plot, double[] ticks) {
		return plot.y + plot.height - (int) Math.round((value - ticks[0]) * plot.height / (ticks[ticks.length - 1] - ticks[0]));
	}
	/** Format only: never alter API instants, range windows, or stored date-only buckets. */
	static String axisTime(Range range, long timestamp, ZoneId localZone) {
		String pattern = range.intraday ? "HH:mm z" : range == Range.YEAR ? "MMM yy" : "dd MMM";
		return format(pattern, range.intraday ? localZone : ZoneOffset.UTC).format(Instant.ofEpochMilli(timestamp));
	}

	static String hoverTime(Range range, long timestamp, ZoneId localZone) {
		Instant instant = Instant.ofEpochMilli(timestamp);
		if (!range.intraday) {
			return format("dd MMM yyyy", ZoneOffset.UTC).format(instant);
		}
		// The offset distinguishes the repeated local hour during the autumn DST transition.
		return format("dd MMM yyyy HH:mm z", localZone).format(instant)
				+ " (" + format("xxx", localZone).format(instant) + ")";
	}

	private static DateTimeFormatter format(String pattern, ZoneId zone) {
		return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone);
	}
	private static String exact(Long value) { return value == null ? "n/a" : String.format(Locale.ENGLISH, "%,d", value); }
	static String compact(double value) {
		if (value >= 1_000_000_000) { return String.format(Locale.ENGLISH, "%.2fB", value / 1_000_000_000); }
		if (value >= 1_000_000) { return String.format(Locale.ENGLISH, "%.2fM", value / 1_000_000); }
		if (value >= 10_000) { return String.format(Locale.ENGLISH, "%.1fk", value / 1_000); }
		return String.format(Locale.ENGLISH, "%,.0f", value);
	}
	private static void legend(Graphics2D g, String label, int x, Color color) {
		g.setColor(color); g.fillRect(x, 20, 11, 3); g.drawString(label, x + 16, 25);
	}
	private static void message(Graphics2D g, String message, int width) {
		g.setFont(FONT.deriveFont(Font.BOLD, 12f));
		g.setColor(MUTED); g.drawString(message, (width - g.getFontMetrics().stringWidth(message)) / 2, 68);
	}
	private static void right(Graphics2D g, String label, int edge, int baseline) {
		g.drawString(label, edge - g.getFontMetrics().stringWidth(label), baseline);
	}
}
