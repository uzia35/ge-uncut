package app.geuncut.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

import app.geuncut.dto.MoverEntry;

class MoversList extends JComponent {
	private static final int ROW_HEIGHT = 36;
	private static final int ICON = 20;
	private static final int TEXT_X = ICON + 8;
	private static final int NAME_GAP = 8;

	interface IconProvider {
		Image icon(int itemId, Runnable onLoaded);
	}

	private final Font numberFont;
	private final Color pctColor;
	private final IconProvider icons;
	private final IntConsumer onOpen;
	private final Function<MoverEntry, String> valueText;
	private final List<Row> rows = new ArrayList<>();

	MoversList(Font numberFont, Color pctColor, IconProvider icons, IntConsumer onOpen) {
		this(numberFont, pctColor, icons, onOpen,
				entry -> String.format("%+.1f%%", entry.getChangePct()));
	}

	MoversList(Font numberFont, Color pctColor, IconProvider icons, IntConsumer onOpen,
			Function<MoverEntry, String> valueText) {
		this.numberFont = numberFont;
		this.pctColor = pctColor;
		this.icons = icons;
		this.onOpen = onOpen;
		this.valueText = valueText;
		setOpaque(true);
		setBackground(Theme.SURFACE);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				int index = event.getY() / ROW_HEIGHT;
				if (onOpen != null && event.getY() >= 0 && index < rows.size()) {
					onOpen.accept(rows.get(index).itemId);
				}
			}
		});
	}

	void setEntries(List<MoverEntry> next) {
		rows.clear();
		if (next != null) {
			for (MoverEntry entry : next) {
				if (entry == null || entry.getName() == null) {
					continue;
				}
				Row row = new Row(entry.getName(), valueText.apply(entry),
						detailLine(entry), entry.getItemId());
				row.icon = icons.icon(entry.getItemId(), this::repaint);
				rows.add(row);
			}
		}
		int height = rows.size() * ROW_HEIGHT;
		setPreferredSize(new Dimension(10, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		revalidate();
		repaint();
	}

	private static String detailLine(MoverEntry entry) {
		if (entry.getPrice() == null) {
			return null;
		}
		String line = String.format("%,d gp", entry.getPrice());
		if (entry.getVolumeDay() != null && entry.getVolumeDay() > 0) {
			line += " · " + shortAmount(entry.getVolumeDay()) + " vol";
		}
		return line;
	}

	private static String shortAmount(long value) {
		if (value >= 1_000_000) {
			return String.format("%.1fM", value / 1_000_000.0);
		}
		if (value >= 1_000) {
			return Math.round(value / 1000.0) + "k";
		}
		return Long.toString(value);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
		if (rows.isEmpty()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		FontMetrics nameMetrics = g2.getFontMetrics(Theme.BODY);
		FontMetrics pctMetrics = g2.getFontMetrics(numberFont);
		FontMetrics detailMetrics = g2.getFontMetrics(Theme.NUM_SMALL);
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			int rowY = index * ROW_HEIGHT;
			int line1 = rowY + 14;
			int line2 = rowY + 29;
			if (row.icon != null) {
				g2.drawImage(row.icon, 0, rowY + (ROW_HEIGHT - ICON) / 2, ICON, ICON, null);
			}
			g2.setFont(Theme.BODY);
			int arrowX = getWidth() - nameMetrics.stringWidth("↗");
			g2.setColor(Theme.MUTED);
			g2.drawString("↗", arrowX, line1);
			g2.setColor(Theme.INK);
			g2.drawString(truncate(row.name, nameMetrics, arrowX - NAME_GAP - TEXT_X), TEXT_X, line1);
			g2.setFont(numberFont);
			g2.setColor(pctColor);
			int pctX = getWidth() - pctMetrics.stringWidth(row.pct);
			g2.drawString(row.pct, pctX, line2);
			if (row.detail != null) {
				g2.setFont(Theme.NUM_SMALL);
				g2.setColor(Theme.INK);
				g2.drawString(truncate(row.detail, detailMetrics, pctX - NAME_GAP - TEXT_X), TEXT_X, line2);
			}
		}
		g2.dispose();
	}

	private static String truncate(String text, FontMetrics metrics, int maxWidth) {
		if (maxWidth <= 0 || metrics.stringWidth(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = metrics.stringWidth("…");
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {
			end--;
		}
		return text.substring(0, Math.max(0, end)) + "…";
	}

	static final class Row {
		private final String name;
		private final String pct;
		private final String detail;
		private final int itemId;
		private Image icon;

		Row(String name, String pct, String detail, int itemId) {
			this.name = name;
			this.pct = pct;
			this.detail = detail;
			this.itemId = itemId;
		}
	}
}
