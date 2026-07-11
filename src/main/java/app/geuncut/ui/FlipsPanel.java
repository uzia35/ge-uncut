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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import app.geuncut.dto.DemandTrend;
import app.geuncut.dto.Fill;
import app.geuncut.dto.Flip;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.MoverEntry;
import app.geuncut.dto.Movers;
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
	private final JLabel unlinkLink = text("Unlink", Theme.FAINT, Theme.SMALL);
	private final JLabel allTimeValue = statValue("0");
	private final JLabel todayValue = statValue("0");
	private final JLabel winValue = statValue("—");
	private final JLabel unrealizedValue = statValue("0");
	private final JLabel roiValue = statValue("—");
	private final JLabel flipsValue = statValue("0");

	private final JPanel moversSection = new JPanel();
	private final JPanel gainersGroup = new JPanel();
	private final JPanel losersGroup = new JPanel();
	private final MoversRotator gainers;
	private final MoversRotator losers;

	private final JPanel offersSection = new JPanel();
	private final JPanel offersList = listPanel();
	private final JLabel offersCount = new JLabel("0");

	private final JPanel activeSection = new JPanel();
	private final JPanel activeList = listPanel();
	private final JLabel activeCount = new JLabel("0");

	private final FlatSelect scanPicker = new FlatSelect(
			new String[] { "Standard", "Fast Fill", "High Volume" },
			new String[] { "standard", "fast", "value" }, 2);
	private final FlatSelect riskPicker = new FlatSelect(new String[] { "Conservative", "Balanced", "Aggressive" }, 1);
	private final JLabel finderStatus = new JLabel("", SwingConstants.CENTER);
	private final JPanel finderList = listPanel();
	private final JButton linkButton = styledButton("Link account");
	private final JLabel upsell = text("Link to unlock members flips ↗", Theme.MUTED, Theme.SMALL);

	public FlipsPanel(Runnable onRefresh, Runnable onLink, Runnable onUnlink, Runnable onOpenMovers, ItemIconLoader iconLoader, IntConsumer onOpenItem) {
		this.iconLoader = iconLoader;
		this.onOpenItem = onOpenItem;
		// Row icons are the plain inventory sprites (no network); the loader repaints
		// the strip when a sprite finishes decoding.
		gainers = new MoversRotator(Theme.NUM_SMALL, Theme.UP, iconLoader::sprite);
		losers = new MoversRotator(Theme.NUM_SMALL, Theme.DOWN, iconLoader::sprite);

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

		// Today's GE movers: top risers and fallers by day change. Public, so it fills
		// in for linked and unlinked players alike. Each group pages through its full
		// list on a timer rather than scrolling, so nothing moves per frame.
		moversSection.setLayout(new BorderLayout());
		moversSection.setBackground(Theme.SURFACE);
		moversSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		moversSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		JPanel moversHeader = sectionHeader("Today's movers ↗", null);
		installRecursively(moversHeader, new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onOpenMovers.run();
			}
		});
		moversSection.add(moversHeader, BorderLayout.NORTH);
		buildMoverGroup(gainersGroup, "Gainers", gainers, 0);
		buildMoverGroup(losersGroup, "Losers", losers, 8);
		JPanel moversBody = new JPanel();
		moversBody.setLayout(new BoxLayout(moversBody, BoxLayout.Y_AXIS));
		moversBody.setBackground(Theme.SURFACE);
		moversBody.setAlignmentX(Component.LEFT_ALIGNMENT);
		moversBody.add(gainersGroup);
		moversBody.add(losersGroup);
		moversSection.add(moversBody, BorderLayout.CENTER);
		moversSection.setVisible(false);
		column.add(moversSection);

		// The section spacing is a bottom border, not a separate strut, so a hidden
		// section leaves no orphaned gap (e.g. an unlinked account with no offers).
		offersSection.setLayout(new BorderLayout());
		offersSection.setBackground(Theme.SURFACE);
		offersSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		offersSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		offersSection.add(sectionHeader("Working offers", offersCount), BorderLayout.NORTH);
		offersSection.add(wrapList(offersList), BorderLayout.CENTER);
		offersSection.setVisible(false);
		column.add(offersSection);

		activeSection.setLayout(new BorderLayout());
		activeSection.setBackground(Theme.SURFACE);
		activeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		activeSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		activeSection.add(sectionHeader("Active flips", activeCount), BorderLayout.NORTH);
		activeSection.add(wrapList(activeList), BorderLayout.CENTER);
		activeSection.setVisible(false);
		column.add(activeSection);

		column.add(sectionHeader("Flip finder", null));
		column.add(strut(4));
		JLabel finderHint = text("Tap an item for full details ↗", Theme.FAINT, Theme.SMALL);
		finderHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(finderHint);
		column.add(strut(6));
		column.add(buildFinderBar(onRefresh));
		column.add(strut(10));
		upsell.setAlignmentX(Component.LEFT_ALIGNMENT);
		upsell.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		upsell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		upsell.setVisible(false);
		upsell.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onLink.run();
			}
		});
		column.add(upsell);
		finderStatus.setForeground(Theme.MUTED);
		finderStatus.setFont(Theme.BODY);
		finderStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(finderStatus);
		column.add(wrapList(finderList));

		linkButton.addActionListener(event -> onLink.run());
		unlinkLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		unlinkLink.setVisible(false);
		unlinkLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onUnlink.run();
			}
		});
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
		// Unlink is only meaningful, and only offered, while an account is linked.
		unlinkLink.setVisible(linked);
	}

	public void showOffers(List<GeOffer> offers, Map<Integer, String> itemNames) {
		offersCount.setText(Integer.toString(offers.size()));
		offersList.removeAll();
		for (GeOffer offer : offers) {
			String name = itemNames.getOrDefault(offer.getItemId(), "Item " + offer.getItemId());
			addCard(offersList, offerCard(offer, name));
		}
		offersSection.setVisible(!offers.isEmpty());
		revalidate();
		repaint();
	}

	public void showActiveFlips(PositionsResponse response) {
		// A degraded 200 can omit summary/positions; treat absence as an empty state
		// rather than NPEing the render.
		PositionsSummary summary = response.getSummary() != null
				? response.getSummary() : PositionsSummary.builder().build();
		setStat(allTimeValue, summary.getTotalRealized());
		setStat(todayValue, summary.getTodayRealized());
		setStat(unrealizedValue, summary.getOpenUnrealized());
		winValue.setText(summary.getWinRate() != null ? Math.round(summary.getWinRate()) + "%" : "—");
		setPct(roiValue, summary.getAvgRoi());
		flipsValue.setText(Integer.toString(summary.getFlips()));
		List<Position> positions = response.getPositions() != null
				? response.getPositions() : Collections.emptyList();
		activeCount.setText(Integer.toString(positions.size()));
		activeList.removeAll();
		for (Position position : positions) {
			addCard(activeList, activeFlipCard(position));
		}
		activeSection.setVisible(!positions.isEmpty());
		revalidate();
		repaint();
	}

	public void showMovers(Movers movers) {
		List<MoverEntry> risers = movers != null ? movers.getRisers() : null;
		List<MoverEntry> fallers = movers != null ? movers.getFallers() : null;
		gainers.setEntries(risers);
		losers.setEntries(fallers);
		// Hide an empty group (and its label) so a one-sided day leaves no dangling
		// header; hide the whole section only when neither side has anything.
		boolean hasGainers = risers != null && !risers.isEmpty();
		boolean hasLosers = fallers != null && !fallers.isEmpty();
		gainersGroup.setVisible(hasGainers);
		losersGroup.setVisible(hasLosers);
		moversSection.setVisible(hasGainers || hasLosers);
		revalidate();
		repaint();
	}

	private void buildMoverGroup(JPanel group, String label, MoversRotator rotator, int topGap) {
		group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
		group.setBackground(Theme.SURFACE);
		group.setAlignmentX(Component.LEFT_ALIGNMENT);
		group.setBorder(BorderFactory.createEmptyBorder(topGap, 0, 0, 0));
		JLabel heading = text(label, Theme.FAINT, Theme.SECTION);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		group.add(heading);
		group.add(rotator);
	}

	public void showFlips(List<Flip> flips, boolean linked) {
		List<Flip> safe = flips != null ? flips : Collections.emptyList();
		setLinked(linked);
		// Unlinked callers see the free (non-members) tier, so invite them to link
		// for the members-item flips rather than gating the panel behind an account.
		upsell.setVisible(!linked);
		finderStatus.setText("");
		finderStatus.setVisible(false);
		finderList.removeAll();
		int shown = Math.min(safe.size(), 10);
		for (Flip flip : safe.subList(0, shown)) {
			addCard(finderList, finderCard(flip));
		}
		if (safe.isEmpty()) {
			finderStatus.setVisible(true);
			finderStatus.setText("No flips match your settings right now");
		}
		revalidate();
		repaint();
	}

	public void showStatus(String message) {
		upsell.setVisible(false);
		finderStatus.setVisible(true);
		finderStatus.setText(message);
		finderList.removeAll();
		revalidate();
		repaint();
	}

	public void showLinkPrompt() {
		setLinked(false);
		upsell.setVisible(false);
		finderStatus.setVisible(true);
		finderStatus.setText("<html><div style='text-align:center'>Connect your geuncut.app account to see flips and track trades automatically.</div></html>");
		linkButton.setText("Link account");
		finderList.removeAll();
		addCard(finderList, linkButton);
		revalidate();
		repaint();
	}

	public void showLinkCode(String code) {
		upsell.setVisible(false);
		finderStatus.setVisible(true);
		finderStatus.setText("<html><div style='text-align:center'>Go to <b>geuncut.app/link</b> and enter<br>"
				+ "<span style='font-size:16px;letter-spacing:2px'><b>" + code + "</b></span><br>Waiting for you to confirm...</div></html>");
		linkButton.setText("Open geuncut.app/link");
		finderList.removeAll();
		addCard(finderList, linkButton);
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
		JPanel status = new JPanel();
		status.setLayout(new BoxLayout(status, BoxLayout.X_AXIS));
		status.setBackground(Theme.SURFACE);
		status.add(linkStatus);
		status.add(Box.createHorizontalStrut(8));
		status.add(unlinkLink);
		header.add(brand, BorderLayout.WEST);
		header.add(status, BorderLayout.EAST);
		return header;
	}

	private JPanel buildStats() {
		JPanel stats = new JPanel();
		stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
		stats.setBackground(Theme.SURFACE);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);

		RoundedPanel card = new RoundedPanel(8, Theme.RAISED, Theme.LINE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createEmptyBorder(11, 12, 11, 12));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Headline trio: the three numbers a flipper glances at first. The rest of
		// the portfolio breakdown sits under a divider so the top stays scannable.
		// NUM_BOLD (not the larger NUM_LG) so a wide value like +999.9M still fits the
		// narrow three-up tiles.
		JPanel trio = new JPanel(new GridLayout(1, 3, 8, 0));
		trio.setOpaque(false);
		trio.setAlignmentX(Component.LEFT_ALIGNMENT);
		trio.add(statTile("ALL-TIME", allTimeValue));
		trio.add(statTile("TODAY", todayValue));
		trio.add(statTile("WIN RATE", winValue));
		card.add(trio);

		card.add(Box.createVerticalStrut(11));
		card.add(divider());
		card.add(Box.createVerticalStrut(9));

		card.add(statLine("Open P/L", unrealizedValue));
		card.add(Box.createVerticalStrut(6));
		card.add(statLine("Avg ROI", roiValue));
		card.add(Box.createVerticalStrut(6));
		card.add(statLine("Flips", flipsValue));

		stats.add(card);
		return stats;
	}

	// One headline KPI: faint caption over a centred value.
	private JPanel statTile(String caption, JLabel value) {
		JPanel tile = new JPanel();
		tile.setOpaque(false);
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		JLabel label = text(caption, Theme.FAINT, Theme.SECTION);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		value.setAlignmentX(Component.CENTER_ALIGNMENT);
		tile.add(label);
		tile.add(Box.createVerticalStrut(3));
		tile.add(value);
		return tile;
	}

	// One secondary metric: label left, value right.
	private JPanel statLine(String label, JLabel value) {
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		value.setFont(Theme.NUM);
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(text(label, Theme.MUTED, Theme.BODY), BorderLayout.WEST);
		row.add(value, BorderLayout.EAST);
		return row;
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
		JLabel nameLabel = nameLabel(name);
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

		addLeft(body, nameLabel(flip.getName()));

		String meta = metaLine(flip);
		if (meta != null) {
			body.add(Box.createVerticalStrut(2));
			addLeft(body, text(meta, Theme.FAINT, Theme.SMALL));
		}

		Pill demand = demandPill(flip.getDemandTrend());
		if (demand != null) {
			body.add(Box.createVerticalStrut(4));
			addLeft(body, demand);
		}

		body.add(Box.createVerticalStrut(8));
		addLeft(body, text("Buy " + GP.format(flip.getQuantity()) + " @ " + GP.format(flip.getBuyPrice()), Theme.INK, Theme.NUM_SMALL));
		body.add(Box.createVerticalStrut(3));

		addLeft(body, text("Sell @ " + GP.format(flip.getTargetSellPrice()), Theme.INK, Theme.NUM_SMALL));
		body.add(Box.createVerticalStrut(3));
		addLeft(body, text("+" + shortGp(flip.getProfitPerItem()) + "/ea after tax", Theme.UP, Theme.NUM_SMALL));

		body.add(Box.createVerticalStrut(9));
		addLeft(body, divider());
		body.add(Box.createVerticalStrut(9));
		JPanel stats = new JPanel(new BorderLayout());
		stats.setOpaque(false);
		stats.add(statMini("Profit", text("+" + shortGp(flip.getTotalProfit()), Theme.UP, Theme.NUM_LG),
				Component.LEFT_ALIGNMENT), BorderLayout.WEST);
		stats.add(statMini("ROI / day", text(trimNum(flip.getRoiPerDay()) + "%", Theme.INK, Theme.NUM_BOLD),
				Component.RIGHT_ALIGNMENT), BorderLayout.EAST);
		addLeft(body, stats);

		body.add(Box.createVerticalStrut(8));
		addLeft(body, text("Buy " + fillTime(flip.getBuyFill()) + " · Sell " + fillTime(flip.getSellFill()), Theme.FAINT, Theme.SMALL));

		card.add(body, BorderLayout.CENTER);
		clickable(card, flip.getItemId());
		return card;
	}

	private static void addLeft(JPanel body, JComponent child) {
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(child);
	}

	private static String metaLine(Flip flip) {
		String margin = marginLabel(flip);
		String hold = flip.getHorizon() != null ? "hold ≤ " + flip.getHorizon().getRecommendedDays() + "d" : null;
		if (margin != null && hold != null) {
			return margin + " · " + hold;
		}
		return margin != null ? margin : hold;
	}

	private static String marginLabel(Flip flip) {
		if ("fast".equals(flip.getStrategy()) && flip.getSpreadPct() != null) {
			return trimNum(flip.getSpreadPct()) + "% margin";
		}
		if (flip.getEntryDiscountPct() != null) {
			return trimNum(flip.getEntryDiscountPct()) + "% below";
		}
		if (flip.getSpreadPct() != null) {
			return trimNum(flip.getSpreadPct()) + "% margin";
		}
		return null;
	}

	private Pill demandPill(DemandTrend trend) {
		if (trend == null || trend.getDirection() == null) {
			return null;
		}
		switch (trend.getDirection()) {
			case RISING:
				return pill("Demand rising", Theme.UP);
			case FALLING:
				return pill("Demand falling", Theme.DOWN);
			default:
				return null;
		}
	}

	private static String fillTime(Fill fill) {
		if (fill == null) {
			return "—";
		}
		if (fill.getWorstCaseHours() != null) {
			return fmtHoursShort(fill.getWorstCaseHours());
		}
		return fill.getSpeed() != null ? fill.getSpeed() : "—";
	}

	private static String fmtHoursShort(double hours) {
		double minutes = hours * 60;
		if (minutes < 1) {
			return "<1m";
		}
		if (minutes < 90) {
			return "~" + Math.round(minutes) + "m";
		}
		if (hours < 24) {
			return "~" + trimNum(hours) + "h";
		}
		return "~" + trimNum(hours / 24) + "d";
	}

	private static JComponent divider() {
		JPanel line = new JPanel();
		line.setBackground(Theme.LINE);
		line.setPreferredSize(new Dimension(10, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return line;
	}

	private JPanel statMini(String label, JLabel value, float align) {
		JPanel col = new JPanel();
		col.setOpaque(false);
		col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
		JLabel caption = text(label.toUpperCase(), Theme.FAINT, Theme.SMALL);
		caption.setAlignmentX(align);
		value.setAlignmentX(align);
		col.add(caption);
		col.add(Box.createVerticalStrut(2));
		col.add(value);
		return col;
	}

	private RoundedPanel activeFlipCard(Position position) {
		RoundedPanel card = card();
		card.add(icon(position.getItemId(), position.getName()), BorderLayout.WEST);

		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel line1 = new JPanel(new BorderLayout(6, 0));
		line1.setOpaque(false);
		line1.add(nameLabel(position.getName()), BorderLayout.CENTER);
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
		// BoxLayout, not GridLayout: cards must size to their own content. GridLayout
		// stretched every card to the tallest one, leaving whitespace under short ones.
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.SURFACE);
		return list;
	}

	// Add a full-width card, capped to its own height so the vertical box never
	// stretches it, with a 6px gap above every card after the first.
	private static void addCard(JPanel list, JComponent card) {
		if (list.getComponentCount() > 0) {
			list.add(Box.createVerticalStrut(6));
		}
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		list.add(card);
	}

	// Item name that never widens the card past the panel: a long name clips (with
	// the full name on hover) instead of forcing a minimum width wider than 225px.
	private JLabel nameLabel(String name) {
		JLabel label = text(name, Theme.WHITE, Theme.BODY_BOLD);
		label.setToolTipText(name);
		int height = label.getPreferredSize().height;
		label.setMinimumSize(new Dimension(0, height));
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return label;
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

	private static void setPct(JLabel label, Double value) {
		if (value == null) {
			label.setText("—");
			label.setForeground(Theme.MUTED);
		} else {
			label.setText(signedPct(value));
			label.setForeground(value >= 0 ? Theme.UP : Theme.DOWN);
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
