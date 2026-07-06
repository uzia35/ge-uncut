package app.geuncut.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import app.geuncut.dto.Flip;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class FlipsPanel extends PluginPanel {
	private final JComboBox<String> scanPicker = new JComboBox<>(new String[] { "standard", "fast", "value" });
	private final JLabel status = new JLabel("", SwingConstants.CENTER);
	private final JPanel list = new JPanel();
	private final JButton linkButton = new JButton("Link account");

	public FlipsPanel(Runnable onRefresh, Runnable onLink) {
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(event -> onRefresh.run());

		JPanel header = new JPanel(new GridLayout(1, 2, 6, 0));
		header.add(scanPicker);
		header.add(refresh);
		add(header, BorderLayout.NORTH);

		linkButton.addActionListener(event -> onLink.run());

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(status, BorderLayout.NORTH);
		wrapper.add(list, BorderLayout.CENTER);
		add(wrapper, BorderLayout.CENTER);

		scanPicker.addActionListener(event -> onRefresh.run());
	}

	public void showLinkPrompt() {
		status.setText("<html><div style='text-align:center;padding:6px'>"
				+ "Connect your geuncut.app account to see flips and track trades automatically."
				+ "</div></html>");
		list.removeAll();
		linkButton.setText("Link account");
		linkButton.setEnabled(true);
		list.add(linkButton);
		revalidate();
		repaint();
	}

	public void showLinkCode(String code) {
		status.setText("<html><div style='text-align:center;padding:6px'>"
				+ "Go to <b>geuncut.app/link</b> and enter:<br>"
				+ "<span style='font-size:20px;letter-spacing:2px'><b>" + code + "</b></span><br>"
				+ "Waiting for you to confirm..."
				+ "</div></html>");
		list.removeAll();
		linkButton.setText("Open geuncut.app/link");
		linkButton.setEnabled(true);
		list.add(linkButton);
		revalidate();
		repaint();
	}

	public String selectedScan() {
		return (String) scanPicker.getSelectedItem();
	}

	public void showStatus(String message) {
		status.setText("<html><div style='text-align:center;padding:6px'>" + message + "</div></html>");
		list.removeAll();
		revalidate();
		repaint();
	}

	public void showFlips(List<Flip> flips) {
		status.setText("");
		list.removeAll();
		NumberFormat gp = NumberFormat.getIntegerInstance();
		int shown = Math.min(flips.size(), 10);
		for (Flip flip : flips.subList(0, shown)) {
			list.add(card(flip, gp));
			list.add(Box.createVerticalStrut(6));
		}
		if (flips.isEmpty()) {
			status.setText("No flips match your settings right now");
		}
		revalidate();
		repaint();
	}

	private JPanel card(Flip flip, NumberFormat gp) {
		JPanel card = new JPanel(new GridLayout(0, 1, 0, 2));
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = new JLabel(flip.getName());
		name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD));
		card.add(name);
		card.add(new JLabel("Buy " + gp.format(flip.getQuantity()) + " @ " + gp.format(flip.getBuyPrice())));
		card.add(new JLabel("Sell @ " + gp.format(flip.getTargetSellPrice())));
		card.add(new JLabel("Profit if filled: " + gp.format(flip.getTotalProfit()) + " gp"));
		return card;
	}
}
