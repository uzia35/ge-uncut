package app.geuncut.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.ItemChartData.Range;

/** Report one actual bucket, never imply that sparse observations cover a whole range. */
final class ItemVolumeDetails {

	static ItemChartData.Point latest(Snapshot snapshot, Range range) {
		if (snapshot == null || snapshot.getStatus() != Status.READY || snapshot.getHistory() == null) { return null; }
		ItemChartData data = ItemChartData.from(snapshot.getHistory(), range);
		return data.getPoints().isEmpty() ? null : data.getPoints().get(data.getPoints().size() - 1);
	}

	static String period(ItemChartData.Point point, Range range, ZoneId zone) {
		if (!range.intraday) {
			return ItemHistoryChart.hoverTime(range, point.getTime(), zone) + " · 00:00–24:00 UTC";
		}
		return ItemHistoryChart.hoverTime(range, point.getTime(), zone) + " · 5 minutes";
	}

	static String quantity(Long value) {
		return value == null ? "Not reported" : String.format(Locale.ENGLISH, "%,d items", value);
	}

	static String coverage(ItemChartData.Point point, Range range, Instant now) {
		boolean current = now.toEpochMilli() >= point.getTime()
				&& now.toEpochMilli() < point.getTime() + range.bucketMs;
		return (current ? "In progress · " : "") + "Reported bucket only · not a " + range.label + " total";
	}

	void render(Graphics2D target, Rectangle area, Snapshot snapshot, Range range) {
		Graphics2D g = (Graphics2D) target.create();
		try {
			g.translate(area.x, area.y);
			ItemHistoryChart.prepare(g);
			ItemChartData.Point point = latest(snapshot, range);
			if (point == null) {
				String message = snapshot == null || snapshot.getStatus() == Status.IDLE || snapshot.getStatus() == Status.LOADING
						? "Loading volume details…" : snapshot.getStatus() == Status.ERROR
						? "Volume details unavailable" : "No " + range.label + " volume observations";
				ItemHistoryChart.label(g, message, (area.width - g.getFontMetrics().stringWidth(message)) / 2, 68, ItemHistoryChart.MUTED);
				return;
			}
			ItemHistoryChart.label(g, range.intraday ? "Volume · latest 5-minute bucket" : "Volume · latest daily bucket",
					0, 12, ItemHistoryChart.INK);
			g.setFont(ItemHistoryChart.FONT.deriveFont(10f));
			wrappedLabel(g, period(point, range, ZoneId.systemDefault()), 28, area.width);
			int cardWidth = (area.width - 8) / 2;
			card(g, new Rectangle(0, 43, cardWidth, 57), "Buy", point.getBuyVolume(), ItemHistoryChart.BUY);
			card(g, new Rectangle(cardWidth + 8, 43, cardWidth, 57), "Sell", point.getSellVolume(), ItemHistoryChart.SELL);
			g.setFont(ItemHistoryChart.FONT.deriveFont(10f));
			wrappedLabel(g, coverage(point, range, Instant.now()), 119, area.width);
			ItemHistoryChart.label(g, "Buy / Sell sides match the chart.", 0, 149, ItemHistoryChart.MUTED);
		} finally { g.dispose(); }
	}

	private static void wrappedLabel(Graphics2D g, String text, int baseline, int width) {
		String line = "";
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && g.getFontMetrics().stringWidth(next) > width) {
				ItemHistoryChart.label(g, line, 0, baseline, ItemHistoryChart.MUTED);
				baseline += 12;
				line = word;
			} else { line = next; }
		}
		ItemHistoryChart.label(g, line, 0, baseline, ItemHistoryChart.MUTED);
	}

	private static void card(Graphics2D g, Rectangle area, String label, Long quantity, Color color) {
		g.setColor(ItemHistoryChart.RAISED);
		g.fillRoundRect(area.x, area.y, area.width, area.height, 8, 8);
		g.setFont(ItemHistoryChart.FONT);
		ItemHistoryChart.label(g, label + " (items)", area.x + 10, area.y + 17, color);
		String value = quantity == null ? "Not reported" : String.format(Locale.ENGLISH, "%,d", quantity);
		g.setFont(ItemHistoryChart.FONT.deriveFont(Font.BOLD, 17f));
		while (g.getFontMetrics().stringWidth(value) > area.width - 20 && g.getFont().getSize() > 8) {
			g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - 1));
		}
		ItemHistoryChart.label(g, value, area.x + 10, area.y + 42, quantity == null ? ItemHistoryChart.MUTED : ItemHistoryChart.INK);
		g.setFont(ItemHistoryChart.FONT);
	}
}
