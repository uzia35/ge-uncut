package app.geuncut.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

import app.geuncut.dto.MoverEntry;

/**
 * Shows a page of movers (icon, name, change%) and, when there are more than fit
 * on a page, fades to the next page on a timer. The transition is an alpha
 * dip-out then dip-in, not a scroll, so nothing moves per frame and there is no
 * motion to stutter.
 */
class MoversRotator extends JComponent {
	private static final int PAGE = 5;
	private static final int DWELL_MS = 4500;
	private static final int FADE_MS = 650;
	private static final int TICK_MS = 16;
	private static final int ROW_HEIGHT = 22;
	private static final int ICON = 18;
	private static final int TEXT_X = ICON + 8;
	private static final int NAME_GAP = 8;

	/** Supplies the row icon and repaints the strip once the sprite decodes. */
	interface IconProvider {
		Image icon(int itemId, Runnable onLoaded);
	}

	private final Font numberFont;
	private final Color pctColor;
	private final IconProvider icons;
	private final List<Row> rows = new ArrayList<>();
	private final Timer timer;

	private int page;
	private int fadingFrom = -1; // page dipping out, or -1 when settled
	private long fadeStartNanos;
	private long pageShownNanos;

	MoversRotator(Font numberFont, Color pctColor, IconProvider icons) {
		this.numberFont = numberFont;
		this.pctColor = pctColor;
		this.icons = icons;
		setOpaque(true);
		setBackground(Theme.SURFACE);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		timer = new Timer(TICK_MS, event -> tick());
	}

	void setEntries(List<MoverEntry> next) {
		rows.clear();
		if (next != null) {
			for (MoverEntry entry : next) {
				Row row = new Row(entry.getName(), String.format("%+.1f%%", entry.getChangePct()));
				// Fetch the sprite once here (not per paint) so onLoaded is registered once.
				row.icon = icons.icon(entry.getItemId(), this::repaint);
				rows.add(row);
			}
		}
		page = 0;
		fadingFrom = -1;
		pageShownNanos = nowNanos();
		int visibleRows = totalPages() > 1 ? PAGE : rows.size();
		Dimension size = new Dimension(10, visibleRows * ROW_HEIGHT);
		setPreferredSize(size);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, visibleRows * ROW_HEIGHT));
		if (totalPages() > 1) {
			if (!timer.isRunning()) {
				timer.start();
			}
		} else {
			timer.stop();
		}
		revalidate();
		repaint();
	}

	private int totalPages() {
		return rows.isEmpty() ? 0 : (rows.size() + PAGE - 1) / PAGE;
	}

	// Package-private so the offscreen render harness can step the animation.
	void tick() {
		long now = nowNanos();
		if (fadingFrom >= 0) {
			if (now - fadeStartNanos >= FADE_MS * 1_000_000L) {
				fadingFrom = -1;
				pageShownNanos = now;
			}
			repaint();
		} else if (totalPages() > 1 && now - pageShownNanos >= DWELL_MS * 1_000_000L) {
			fadingFrom = page;
			page = (page + 1) % totalPages();
			fadeStartNanos = now;
			repaint();
		}
	}

	// Overridable clock so the harness can drive the fade deterministically.
	long nowNanos() {
		return System.nanoTime();
	}

	@Override
	public void removeNotify() {
		timer.stop();
		super.removeNotify();
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
		if (fadingFrom >= 0) {
			// Dip-out the old page for the first half, dip-in the new page for the
			// second, so the two never overlap (which would smear the text).
			double progress = (nowNanos() - fadeStartNanos) / (FADE_MS * 1_000_000.0);
			progress = Math.max(0.0, Math.min(1.0, progress));
			if (progress < 0.5) {
				paintPage(g2, fadingFrom, (float) (1.0 - progress * 2.0));
			} else {
				paintPage(g2, page, (float) (progress * 2.0 - 1.0));
			}
		} else {
			paintPage(g2, page, 1f);
		}
		g2.dispose();
	}

	private void paintPage(Graphics2D g2, int pageIndex, float alpha) {
		if (alpha <= 0f) {
			return;
		}
		Composite base = g2.getComposite();
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
		FontMetrics nameMetrics = g2.getFontMetrics(Theme.BODY);
		FontMetrics pctMetrics = g2.getFontMetrics(numberFont);
		int start = pageIndex * PAGE;
		int end = Math.min(start + PAGE, rows.size());
		for (int index = start; index < end; index++) {
			Row row = rows.get(index);
			int rowY = (index - start) * ROW_HEIGHT;
			int baseline = rowY + (ROW_HEIGHT + nameMetrics.getAscent() - nameMetrics.getDescent()) / 2;
			if (row.icon != null) {
				g2.drawImage(row.icon, 0, rowY + (ROW_HEIGHT - ICON) / 2, ICON, ICON, null);
			}
			// Change% right-aligned, drawn first so the name knows how much room is left.
			g2.setFont(numberFont);
			g2.setColor(pctColor);
			int pctX = getWidth() - pctMetrics.stringWidth(row.pct);
			g2.drawString(row.pct, pctX, baseline);
			g2.setFont(Theme.BODY);
			g2.setColor(Theme.INK);
			g2.drawString(truncate(row.name, nameMetrics, pctX - NAME_GAP - TEXT_X), TEXT_X, baseline);
		}
		g2.setComposite(base);
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
		private Image icon;

		Row(String name, String pct) {
			this.name = name;
			this.pct = pct;
		}
	}
}
