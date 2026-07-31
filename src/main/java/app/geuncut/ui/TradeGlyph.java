package app.geuncut.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * The History outcome glyph, matching the website: swap arrows for a completed
 * flip, a single arrow for a one-way trade. Painted vector, tinted by caller.
 */
class TradeGlyph implements Icon {
	private final boolean flipped;
	private final Color color;
	private final int size;

	TradeGlyph(boolean flipped, Color color, int size) {
		this.flipped = flipped;
		this.color = color;
		this.size = size;
	}

	@Override
	public void paintIcon(Component component, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(color);
		float unit = size / 24f;
		g2.setStroke(new BasicStroke(2.4f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.translate(x, y);
		if (flipped) {
			drawArrow(g2, unit, 3, 21, 8, true);
			drawArrow(g2, unit, 21, 3, 16, false);
		} else {
			drawArrow(g2, unit, 4, 20, 12, true);
		}
		g2.dispose();
	}

	// A horizontal shaft with a 4-unit chevron head at the pointed end.
	private static void drawArrow(Graphics2D g2, float unit, int fromX, int toX, int yUnits, boolean right) {
		int y = Math.round(yUnits * unit);
		g2.drawLine(Math.round(fromX * unit), y, Math.round(toX * unit), y);
		int head = Math.round(4 * unit);
		int tipX = Math.round(toX * unit);
		int backX = right ? tipX - head : tipX + head;
		g2.drawLine(backX, y - head, tipX, y);
		g2.drawLine(backX, y + head, tipX, y);
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}
}
