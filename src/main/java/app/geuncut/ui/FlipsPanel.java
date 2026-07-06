package app.geuncut.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import app.geuncut.dto.Flip;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.Position;
import app.geuncut.dto.PositionsResponse;
import app.geuncut.dto.PositionsSummary;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * The GE Uncut side panel, styled to the website: near-black surfaces, a
 * monochrome accent, monospace numerals. One scrolling column of sections,
 * summary first, then live GE state, then the flip finder.
 */
public class FlipsPanel extends PluginPanel {
	private static final NumberFormat GP = NumberFormat.getIntegerInstance();

	private final ItemIconLoader iconLoader;
	private final IntConsumer onOpenItem;

	private final JLabel linkStatus = new JLabel();
	private final JLabel allTimeValue = statValue("—");
	private final JLabel statsSubtitle = new JLabel("", SwingConstants.CENTER);
	private final JLabel openValue = statValue("—");
	private final JLabel slotsValue = statValue("0/8");
	private final JLabel todayValue = statValue("—");

	private final JPanel offersSection = new JPanel();
	private final JPanel offersList = listPanel();
	private final JLabel offersCount = new JLabel("0");

	private final JPanel activeSection = new JPanel();
	private final JPanel activeList = listPanel();
	private final JLabel activeCount = new JLabel("0");

	private final FlatSelect scanPicker = new FlatSelect(new String[] { "standard", "fast", "value" }, 0);
	private final FlatSelect riskPicker = new FlatSelect(new String[] { "Conservative", "Balanced", "Aggressive" }, 1);
	private final JLabel finderStatus = new JLabel("", SwingConstants.CENTER);
	private final JPanel finderList = listPanel();
	private final JButton linkButton = styledButton("Link account");

	public FlipsPanel(Runnable onRefresh, Runnable onLink, ItemIconLoader iconLoader, IntConsumer onOpenItem) {
		this.iconLoader = iconLoader;
		this.onOpenItem = onOpenItem;

		setLayout(new BorderLayout());
		setBackground(Theme.SURFACE);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(Theme.SURFACE);

		column.add(buildHeader());
		column.add(strut(12));
		column.add(buildStats());
		column.add(strut(14));

		offersSection.setLayout(new BorderLayout());
		offersSection.setBackground(Theme.SURFACE);
		offersSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		offersSection.add(sectionHeader("Working offers", offersCount), BorderLayout.NORTH);
		offersSection.add(wrapList(offersList), BorderLayout.CENTER);
		offersSection.setVisible(false);
		column.add(offersSection);
		column.add(strut(14));

		activeSection.setLayout(new BorderLayout());
		activeSection.setBackground(Theme.SURFACE);
		activeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		activeSection.add(sectionHeader("Active flips", activeCount), BorderLayout.NORTH);
		activeSection.add(wrapList(activeList), BorderLayout.CENTER);
		activeSection.setVisible(false);
		column.add(activeSection);
		column.add(strut(14));

		column.add(sectionHeader("Flip finder", null));
		column.add(strut(4));
		JLabel finderHint = text("Click any item → geuncut.app ↗", Theme.FAINT, Theme.SMALL);
		finderHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(finderHint);
		column.add(strut(6));
		column.add(buildFinderBar(onRefresh));
		column.add(strut(6));
		finderStatus.setForeground(Theme.MUTED);
		finderStatus.setFont(Theme.BODY);
		finderStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(finderStatus);
		column.add(wrapList(finderList));

		linkButton.addActionListener(event -> onLink.run());
		scanPicker.setOnChange(onRefresh);
		riskPicker.setOnChange(onRefresh);

		add(column, BorderLayout.NORTH);
		setLinked(true);
	}

	// ---- public updates (call on the EDT) ------------------------------------

	public String selectedScan() {
		return scanPicker.selectedValue();
	}

	public String selectedRisk() {
		return riskPicker.selectedValue().toLowerCase();
	}

	public void setLinked(boolean linked) {
		linkStatus.setText(linked
				? "<html><span style='color:#2ec27e'>&#9679;</span> <span style='color:#9a9aa4'>Linked</span></html>"
				: "<html><span style='color:#9a9aa4'>Not linked</span></html>");
	}

	public void showOffers(List<GeOffer> offers, Map<Integer, String> itemNames) {
		slotsValue.setText(offers.size() + "/8");
		offersCount.setText(Integer.toString(offers.size()));
		offersList.removeAll();
		for (GeOffer offer : offers) {
			String name = itemNames.getOrDefault(offer.getItemId(), "Item " + offer.getItemId());
			offersList.add(offerCard(offer, name));
		}
		offersSection.setVisible(!offers.isEmpty());
		revalidate();
		repaint();
	}

	public void showActiveFlips(PositionsResponse response) {
		PositionsSummary summary = response.getSummary();
		setStat(allTimeValue, summary.getTotalRealized());
		setStat(todayValue, summary.getTodayRealized());
		setStat(openValue, summary.getOpenUnrealized());
		statsSubtitle.setText(subtitleText(summary));
		List<Position> positions = response.getPositions();
		activeCount.setText(Integer.toString(positions.size()));
		activeList.removeAll();
		for (Position position : positions) {
			activeList.add(activeFlipCard(position));
		}
		activeSection.setVisible(!positions.isEmpty());
		revalidate();
		repaint();
	}

	public void showFlips(List<Flip> flips) {
		finderStatus.setText("");
		finderStatus.setVisible(false);
		finderList.removeAll();
		int shown = Math.min(flips.size(), 10);
		for (Flip flip : flips.subList(0, shown)) {
			finderList.add(finderCard(flip));
		}
		if (flips.isEmpty()) {
			finderStatus.setVisible(true);
			finderStatus.setText("No flips match your settings right now");
		}
		revalidate();
		repaint();
	}

	public void showStatus(String message) {
		finderStatus.setVisible(true);
		finderStatus.setText(message);
		finderList.removeAll();
		revalidate();
		repaint();
	}

	public void showLinkPrompt() {
		setLinked(false);
		finderStatus.setVisible(true);
		finderStatus.setText("<html><div style='text-align:center'>Connect your geuncut.app account to see flips and track trades automatically.</div></html>");
		linkButton.setText("Link account");
		finderList.removeAll();
		finderList.add(linkButton);
		revalidate();
		repaint();
	}

	public void showLinkCode(String code) {
		finderStatus.setVisible(true);
		finderStatus.setText("<html><div style='text-align:center'>Go to <b>geuncut.app/link</b> and enter<br>"
				+ "<span style='font-size:16px;letter-spacing:2px'><b>" + code + "</b></span><br>Waiting for you to confirm...</div></html>");
		linkButton.setText("Open geuncut.app/link");
		finderList.removeAll();
		finderList.add(linkButton);
		revalidate();
		repaint();
	}

	// ---- section builders ----------------------------------------------------

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(Theme.SURFACE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel brand = new JPanel(new BorderLayout(8, 0));
		brand.setBackground(Theme.SURFACE);
		BufferedImage emblem = ImageUtil.loadImageResource(FlipsPanel.class, "/geuncut_icon.png");
		JLabel logo = new JLabel(new ImageIcon(ImageUtil.resizeImage(emblem, 20, 20)));
		JLabel name = text("GE Uncut", Theme.WHITE, Theme.BODY_BOLD);
		brand.add(logo, BorderLayout.WEST);
		brand.add(name, BorderLayout.CENTER);

		linkStatus.setFont(Theme.SMALL);
		header.add(brand, BorderLayout.WEST);
		header.add(linkStatus, BorderLayout.EAST);
		return header;
	}

	private JPanel buildStats() {
		JPanel stats = new JPanel();
		stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
		stats.setBackground(Theme.SURFACE);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Hero: lifetime realized profit, with a win-rate / flips subtitle. This
		// is the number a flipper actually cares about, not a single session.
		RoundedPanel hero = new RoundedPanel(8, Theme.RAISED, Theme.LINE);
		hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
		hero.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
		hero.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel heroLabel = text("ALL-TIME PROFIT", Theme.FAINT, Theme.SMALL);
		heroLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		allTimeValue.setFont(Theme.NUM_HERO);
		allTimeValue.setAlignmentX(Component.CENTER_ALIGNMENT);
		statsSubtitle.setForeground(Theme.MUTED);
		statsSubtitle.setFont(Theme.NUM_TINY);
		statsSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		hero.add(heroLabel);
		hero.add(Box.createVerticalStrut(2));
		hero.add(allTimeValue);
		hero.add(Box.createVerticalStrut(3));
		hero.add(statsSubtitle);
		stats.add(hero);
		stats.add(Box.createVerticalStrut(7));

		JPanel row = new JPanel(new GridLayout(1, 3, 7, 0));
		row.setBackground(Theme.SURFACE);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(statCell("Today", todayValue));
		row.add(statCell("Open", openValue));
		row.add(statCell("Slots", slotsValue));
		stats.add(row);
		return stats;
	}

	private JPanel buildFinderBar(Runnable onRefresh) {
		JPanel bar = new JPanel(new BorderLayout(6, 0));
		bar.setBackground(Theme.SURFACE);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Two equal-width dropdowns stacked, with one refresh button beside them
		// spanning both rows, so scan and risk line up instead of one being short.
		JPanel pickers = new JPanel();
		pickers.setLayout(new BoxLayout(pickers, BoxLayout.Y_AXIS));
		pickers.setBackground(Theme.SURFACE);
		pickers.add(scanPicker);
		pickers.add(Box.createVerticalStrut(6));
		pickers.add(riskPicker);

		bar.add(pickers, BorderLayout.CENTER);
		bar.add(flatButton("↻", onRefresh), BorderLayout.EAST);
		return bar;
	}

	private RoundedPanel flatButton(String glyph, Runnable onClick) {
		RoundedPanel button = new RoundedPanel(7, Theme.RAISED, Theme.LINE);
		button.setLayout(new BorderLayout());
		button.setBorder(BorderFactory.createEmptyBorder(5, 11, 5, 11));
		JLabel label = new JLabel(glyph, SwingConstants.CENTER);
		label.setForeground(Theme.MUTED);
		label.setFont(Theme.BODY);
		button.add(label, BorderLayout.CENTER);
		installHoverClick(button, onClick);
		return button;
	}

	// ---- cards ---------------------------------------------------------------

	private RoundedPanel offerCard(GeOffer offer, String name) {
		RoundedPanel card = card();
		card.add(icon(offer.getItemId(), name), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		boolean buy = "buy".equals(offer.getSide());
		JLabel nameLabel = text(name, Theme.WHITE, Theme.BODY_BOLD);
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(nameLabel);
		body.add(Box.createVerticalStrut(6));

		double fraction = offer.getQuantityTotal() > 0
				? (double) offer.getQuantityFilled() / offer.getQuantityTotal() : 0;
		JPanel barRow = new JPanel(new BorderLayout(7, 0));
		barRow.setOpaque(false);
		barRow.add(buy ? pill("BUY", Theme.INFO) : pill("SELL", Theme.AMBER), BorderLayout.WEST);
		FillBar progress = new FillBar(fraction, buy ? Theme.INFO : Theme.AMBER);
		progress.setPreferredSize(new Dimension(10, 6));
		barRow.add(progress, BorderLayout.CENTER);
		barRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(barRow);
		body.add(Box.createVerticalStrut(5));

		JPanel meta = new JPanel(new BorderLayout());
		meta.setOpaque(false);
		meta.add(text(GP.format(offer.getQuantityFilled()) + " / " + GP.format(offer.getQuantityTotal()), Theme.INK, Theme.NUM_SMALL), BorderLayout.WEST);
		meta.add(text("@ " + GP.format(offer.getPriceEach()), Theme.MUTED, Theme.NUM_SMALL), BorderLayout.EAST);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(meta);

		card.add(body, BorderLayout.CENTER);
		clickable(card, offer.getItemId());
		return card;
	}

	private RoundedPanel finderCard(Flip flip) {
		RoundedPanel card = card();
		card.add(icon(flip.getItemId(), flip.getName()), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		// Name on its own line so long names are never clipped.
		JLabel name = text(flip.getName(), Theme.WHITE, Theme.BODY_BOLD);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(name);
		body.add(Box.createVerticalStrut(4));

		// One number per line, each labelled, then profit with the ROI badge.
		JLabel buyLine = text("Buy " + GP.format(flip.getQuantity()) + " @ " + GP.format(flip.getBuyPrice()), Theme.MUTED, Theme.NUM_SMALL);
		buyLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(buyLine);
		body.add(Box.createVerticalStrut(2));

		JLabel sellLine = text("Sell @ " + GP.format(flip.getTargetSellPrice()), Theme.MUTED, Theme.NUM_SMALL);
		sellLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(sellLine);
		body.add(Box.createVerticalStrut(5));

		JPanel profitLine = new JPanel(new BorderLayout(6, 0));
		profitLine.setOpaque(false);
		profitLine.add(text("+" + shortGp(flip.getTotalProfit()), Theme.UP, Theme.NUM_BOLD), BorderLayout.WEST);
		profitLine.add(pill("ROI " + trimNum(flip.getRoiPerDay()) + "%", Theme.UP), BorderLayout.EAST);
		profitLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(profitLine);

		card.add(body, BorderLayout.CENTER);
		clickable(card, flip.getItemId());
		return card;
	}

	private RoundedPanel activeFlipCard(Position position) {
		RoundedPanel card = card();
		card.add(icon(position.getItemId(), position.getName()), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel line1 = new JPanel(new BorderLayout(6, 0));
		line1.setOpaque(false);
		line1.add(text(position.getName(), Theme.WHITE, Theme.BODY_BOLD), BorderLayout.CENTER);
		if (isSell(position.getPhase())) {
			line1.add(pill("SELL", Theme.AMBER), BorderLayout.EAST);
		}
		line1.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(line1);
		body.add(Box.createVerticalStrut(3));

		JLabel sub = text(GP.format(position.getQuantity()) + " @ " + GP.format(position.getBuyPrice()), Theme.MUTED, Theme.NUM_SMALL);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(sub);

		card.add(body, BorderLayout.CENTER);
		card.add(pnlBlock(position), BorderLayout.EAST);
		clickable(card, position.getItemId());
		return card;
	}

	private JPanel pnlBlock(Position position) {
		JPanel block = new JPanel();
		block.setOpaque(false);
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

		Long profit = position.getUnrealizedProfit();
		if (profit == null) {
			JLabel stale = text("—", Theme.FAINT, Theme.NUM_BOLD);
			stale.setAlignmentX(Component.RIGHT_ALIGNMENT);
			block.add(stale);
			return block;
		}
		JLabel value = text(pnlText(profit), profit >= 0 ? Theme.UP : Theme.DOWN, Theme.NUM_BOLD);
		value.setAlignmentX(Component.RIGHT_ALIGNMENT);
		block.add(value);
		if (position.getRoi() != null) {
			JLabel roi = text(signedPct(position.getRoi()), Theme.FAINT, Theme.NUM_SMALL);
			roi.setAlignmentX(Component.RIGHT_ALIGNMENT);
			block.add(roi);
		}
		return block;
	}

	// ---- small helpers -------------------------------------------------------

	private RoundedPanel card() {
		RoundedPanel card = new RoundedPanel(9, Theme.RAISED, Theme.LINE);
		card.setLayout(new BorderLayout(9, 0));
		card.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
		return card;
	}

	private JLabel icon(int itemId, String name) {
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(36, 32));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		iconLoader.load(itemId, name, label, 32);
		return label;
	}

	// Item rows open the full item page on geuncut.app, with a hover highlight so
	// the row reads as tappable. Installed across the whole subtree so a click
	// anywhere in the card counts, not just the padding between labels.
	private void clickable(RoundedPanel card, int itemId) {
		installHoverClick(card, () -> onOpenItem.accept(itemId));
	}

	private static void installHoverClick(RoundedPanel panel, Runnable onClick) {
		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent event) {
				panel.setFill(Theme.HOVER);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				if (!panel.contains(SwingUtilities.convertPoint((Component) event.getSource(), event.getPoint(), panel))) {
					panel.setFill(Theme.RAISED);
				}
			}
		};
		installRecursively(panel, adapter);
	}

	private static void installRecursively(Component component, MouseAdapter adapter) {
		component.addMouseListener(adapter);
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				installRecursively(child, adapter);
			}
		}
	}

	private Pill pill(String label, java.awt.Color hue) {
		return new Pill(label, hue, Theme.soft(hue), Theme.line(hue));
	}

	private JPanel sectionHeader(String title, JLabel count) {
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setBackground(Theme.SURFACE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JLabel titleLabel = text(title.toUpperCase(), Theme.MUTED, Theme.SECTION);
		header.add(titleLabel);
		header.add(Box.createHorizontalStrut(8));
		if (count != null) {
			count.setForeground(Theme.FAINT);
			count.setFont(Theme.NUM_SMALL);
			header.add(count);
			header.add(Box.createHorizontalStrut(8));
		}
		JPanel rule = new JPanel();
		rule.setBackground(Theme.LINE);
		rule.setPreferredSize(new Dimension(10, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		header.add(rule);
		return header;
	}

	private RoundedPanel statCell(String label, JLabel value) {
		RoundedPanel cell = new RoundedPanel(7, Theme.RAISED, Theme.LINE);
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
		JLabel labelText = text(label.toUpperCase(), Theme.FAINT, Theme.SMALL);
		labelText.setAlignmentX(Component.CENTER_ALIGNMENT);
		value.setAlignmentX(Component.CENTER_ALIGNMENT);
		cell.add(labelText);
		cell.add(Box.createVerticalStrut(3));
		cell.add(value);
		return cell;
	}

	private static JLabel statValue(String text) {
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(Theme.WHITE);
		label.setFont(Theme.NUM_BOLD);
		return label;
	}

	private JPanel wrapList(JPanel list) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(Theme.SURFACE);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrapper.add(list, BorderLayout.NORTH);
		return wrapper;
	}

	private static JPanel listPanel() {
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 6));
		list.setBackground(Theme.SURFACE);
		return list;
	}

	private static JLabel text(String value, java.awt.Color color, java.awt.Font font) {
		JLabel label = new JLabel(value);
		label.setForeground(color);
		label.setFont(font);
		return label;
	}

	private static JButton styledButton(String text) {
		JButton button = new JButton(text);
		button.setBackground(Theme.RAISED);
		button.setForeground(Theme.INK);
		button.setFont(Theme.BODY);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Theme.LINE_STRONG),
				BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		return button;
	}

	private static Component strut(int height) {
		return Box.createVerticalStrut(height);
	}

	private static String subtitleText(PositionsSummary summary) {
		if (summary.getFlips() == 0) {
			return "no closed flips yet";
		}
		StringBuilder builder = new StringBuilder();
		if (summary.getWinRate() != null) {
			builder.append(Math.round(summary.getWinRate())).append("% win · ");
		}
		builder.append(summary.getFlips()).append(summary.getFlips() == 1 ? " flip" : " flips");
		if (summary.getAvgRoi() != null) {
			builder.append(" · ").append(signedPct(summary.getAvgRoi())).append(" avg");
		}
		return builder.toString();
	}

	private static boolean isSell(String phase) {
		return "sell".equals(phase) || "exit".equals(phase);
	}

	private static void setStat(JLabel label, long value) {
		if (value == 0) {
			label.setText("0");
			label.setForeground(Theme.WHITE);
		} else if (value > 0) {
			label.setText("+" + shortGp(value));
			label.setForeground(Theme.UP);
		} else {
			label.setText("−" + shortGp(-value));
			label.setForeground(Theme.DOWN);
		}
	}

	private static String pnlText(long value) {
		return (value >= 0 ? "+" : "−") + shortGp(Math.abs(value));
	}

	private static String signedPct(double value) {
		return (value >= 0 ? "+" : "−") + trimNum(Math.abs(value)) + "%";
	}

	private static String trimNum(double value) {
		double rounded = Math.round(value * 10) / 10.0;
		return rounded == Math.floor(rounded) ? Integer.toString((int) rounded) : Double.toString(rounded);
	}

	private static String shortGp(long value) {
		long magnitude = Math.abs(value);
		if (magnitude >= 1_000_000) {
			return String.format("%.1fM", value / 1_000_000.0);
		}
		if (magnitude >= 1_000) {
			return Math.round(value / 1000.0) + "k";
		}
		return Long.toString(value);
	}
}
