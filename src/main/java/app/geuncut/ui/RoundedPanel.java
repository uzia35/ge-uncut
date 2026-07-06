package app.geuncut.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** A panel that paints a rounded, optionally bordered background. */
class RoundedPanel extends JPanel {
	private final int arc;
	private Color fill;
	private final Color border;

	RoundedPanel(int arc, Color fill, Color border) {
		this.arc = arc;
		this.fill = fill;
		this.border = border;
		setOpaque(false);
	}

	void setFill(Color fill) {
		this.fill = fill;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
		if (border != null) {
			g2.setColor(border);
			g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
