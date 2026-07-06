package app.geuncut.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** A slim progress bar: a dark track with a coloured fill for offer progress. */
class FillBar extends JComponent {
	private final double fraction;
	private final Color fill;

	FillBar(double fraction, Color fill) {
		this.fraction = Math.max(0, Math.min(1, fraction));
		this.fill = fill;
		setPreferredSize(new Dimension(10, 6));
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g2.setColor(Theme.TRACK);
		g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
		g2.setColor(Theme.LINE);
		g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
		int fillWidth = (int) Math.round((w - 2) * fraction);
		if (fillWidth > 0) {
			g2.setColor(fill);
			g2.fillRoundRect(1, 1, Math.max(fillWidth, h - 2), h - 2, h, h);
		}
		g2.dispose();
	}
}
