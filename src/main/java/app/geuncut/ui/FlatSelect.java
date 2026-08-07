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
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

class FlatSelect extends RoundedPanel {
	private static final long SAME_CLICK_MS = 60;

	private String[] labels;
	private String[] values;
	private long hiddenAt = -1;
	private JPopupMenu menu;
	private final JLabel valueLabel;
	private int selectedIndex;
	private Runnable onChange = () -> {
	};

	FlatSelect(String[] options, int selectedIndex) {
		this(options, options, selectedIndex);
	}

	FlatSelect(String[] labels, String[] values, int selectedIndex) {
		super(7, Theme.RAISED, Theme.LINE);
		this.labels = labels;
		this.values = values;
		this.selectedIndex = selectedIndex;
		setLayout(new BorderLayout(6, 0));
		setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 18));

		valueLabel = new JLabel(labels[selectedIndex]);
		valueLabel.setForeground(Theme.INK);
		valueLabel.setFont(Theme.BODY);
		add(valueLabel, BorderLayout.CENTER);

		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				toggleMenu();
			}

			@Override
			public void mouseEntered(MouseEvent event) {
				setFill(Theme.HOVER);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				if (!contains(SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(), FlatSelect.this))) {
					setFill(Theme.RAISED);
				}
			}
		};
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(adapter);
		valueLabel.addMouseListener(adapter);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(Theme.FAINT);
		int cx = getWidth() - 12;
		int cy = getHeight() / 2;
		g2.fillPolygon(new int[] { cx - 4, cx + 4, cx }, new int[] { cy - 2, cy - 2, cy + 3 }, 3);
		g2.dispose();
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	@Override
	public void setToolTipText(String text) {
		super.setToolTipText(text);
		valueLabel.setToolTipText(text);
	}

	String selectedValue() {
		return values[selectedIndex];
	}

	void setOptions(String[] labels, String[] values, String desiredValue) {
		this.labels = labels;
		this.values = values;
		selectedIndex = 0;
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(desiredValue)) {
				selectedIndex = i;
				break;
			}
		}
		valueLabel.setText(labels[selectedIndex]);
	}

	private void toggleMenu() {
		if (shouldOpen(System.currentTimeMillis())) {
			showMenu();
		}
	}

	boolean shouldOpen(long now) {
		if (hiddenAt >= 0 && now - hiddenAt <= SAME_CLICK_MS) {
			hiddenAt = -1;
			return false;
		}
		return true;
	}

	void markHidden(long at) {
		hiddenAt = at;
	}

	private void showMenu() {
		JPopupMenu menu = new JPopupMenu();
		this.menu = menu;
		menu.setBackground(Theme.RAISED);
		menu.setBorder(BorderFactory.createLineBorder(Theme.LINE_STRONG));
		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
				markHidden(System.currentTimeMillis());
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent event) {
			}
		});
		for (int i = 0; i < labels.length; i++) {
			JMenuItem item = new JMenuItem(labels[i]);
			item.setBackground(Theme.RAISED);
			item.setForeground(i == selectedIndex ? Theme.WHITE : Theme.INK);
			item.setFont(Theme.BODY);
			item.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
			int index = i;
			item.addActionListener(event -> select(index));
			menu.add(item);
		}
		menu.show(this, 0, getHeight() + 2);
	}

	boolean menuShowing() {
		return menu != null && menu.isVisible();
	}

	JPopupMenu getMenuForTest() {
		return menu;
	}

	void selectLabelForTest(String label) {
		for (int i = 0; i < labels.length; i++) {
			if (labels[i].equals(label)) {
				selectedIndex = i;
				valueLabel.setText(labels[i]);
				return;
			}
		}
		throw new IllegalArgumentException("no such option: " + label);
	}

	private void select(int index) {
		if (index == selectedIndex) {
			return;
		}
		selectedIndex = index;
		valueLabel.setText(labels[index]);
		onChange.run();
	}
}
