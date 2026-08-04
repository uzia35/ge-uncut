package app.geuncut.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

class FlatToggle extends RoundedPanel {
	private static final int TRACK_WIDTH = 24;
	private static final int TRACK_HEIGHT = 13;
	private static final int TRACK_INSET = 10;

	private final JLabel caption;
	private boolean on;
	private Runnable onChange = () -> {
	};

	FlatToggle(String text, boolean on) {
		super(7, Theme.RAISED, Theme.LINE);
		this.on = on;
		setLayout(new BorderLayout(6, 0));
		setBorder(BorderFactory.createEmptyBorder(5, 10, 5, TRACK_WIDTH + TRACK_INSET + 6));

		caption = new JLabel(text);
		caption.setForeground(Theme.INK);
		caption.setFont(Theme.BODY);
		add(caption, BorderLayout.CENTER);

		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				set(!FlatToggle.this.on);
				onChange.run();
			}

			@Override
			public void mouseEntered(MouseEvent event) {
				setFill(Theme.HOVER);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				if (!contains(SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(),
						FlatToggle.this))) {
					setFill(Theme.RAISED);
				}
			}
		};
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(adapter);
		caption.addMouseListener(adapter);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int x = getWidth() - TRACK_WIDTH - TRACK_INSET;
		int y = (getHeight() - TRACK_HEIGHT) / 2;
		g2.setColor(on ? Theme.UP : Theme.LINE_STRONG);
		g2.fillRoundRect(x, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
		int knob = TRACK_HEIGHT - 4;
		g2.setColor(on ? Theme.PILL_INK : Theme.INK);
		g2.fillOval(on ? x + TRACK_WIDTH - knob - 2 : x + 2, y + 2, knob, knob);
		g2.dispose();
	}

	boolean isOn() {
		return on;
	}

	void set(boolean value) {
		on = value;
		repaint();
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	@Override
	public void setToolTipText(String text) {
		super.setToolTipText(text);
		caption.setToolTipText(text);
	}
}
