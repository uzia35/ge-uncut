package app.geuncut.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** A small rounded badge/chip: coloured text on a translucent ground. */
class Pill extends JLabel {
	private final Color ground;
	private final Color border;
	private final Integer arc;

	Pill(String text, Color fg, Color ground, Color border) {
		this(text, fg, ground, border, null);
	}

	/** Fixed corner arc instead of the default full capsule. */
	Pill(String text, Color fg, Color ground, Color border, Integer arc) {
		super(text);
		this.ground = ground;
		this.border = border;
		this.arc = arc;
		setForeground(fg);
		setFont(Theme.NUM_SMALL);
		setBorder(BorderFactory.createEmptyBorder(1, 6, 2, 6));
		setOpaque(false);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		int round = arc != null ? arc : h;
		if (ground != null) {
			g2.setColor(ground);
			g2.fillRoundRect(0, 0, w - 1, h - 1, round, round);
		}
		if (border != null) {
			g2.setColor(border);
			g2.drawRoundRect(0, 0, w - 1, h - 1, round, round);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
