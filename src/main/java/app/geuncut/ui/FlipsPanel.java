package app.geuncut.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
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

	// Usable width inside a card: RuneLite's 225px sidebar minus the panel
	// border (2x10) and the card padding (2x11). Layout math uses this instead
	// of guesses — the harness renders at the same constant.
	private static final int CONTENT_WIDTH = PANEL_WIDTH - 42;

	private final ItemIconLoader iconLoader;
	private final IntConsumer onOpenItem;
	private final LongConsumer onNotFlip;
	private final LongConsumer onRestore;

	private final JLabel linkStatus = new JLabel();
	private final JLabel unlinkLink = text("Unlink", Theme.MUTED, Theme.SMALL);
	private final JLabel linkLink = text("Link", Theme.INFO, Theme.SMALL);
	private final JLabel webHint = text("Follow your flips on geuncut.app ↗", Theme.MUTED, Theme.SMALL);
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

	private final JPanel historySection = new JPanel();
	private final JPanel historyList = listPanel();
	private final JLabel historyCount = new JLabel("0");

	// Tab bar + card content. History is linked-only, so it's hidden when unlinked.
	private final JPanel content = new JPanel(new CardLayout());
	private final Map<String, JLabel> tabButtons = new LinkedHashMap<>();
	private String activeTab = "finder";
	private boolean linked = true;

	// Which active-flip cards are expanded (by position id). Survives the poll's
	// re-render so an open card doesn't snap shut every 30 seconds.
	private final java.util.Set<Long> expandedFlips = new java.util.HashSet<>();
	private List<Position> lastPositions = Collections.emptyList();

	private final FlatSelect scanPicker = new FlatSelect(
			new String[] { "Standard", "Fast Fill", "High Volume" },
			new String[] { "standard", "fast", "value" }, 2);
	private final FlatSelect riskPicker = new FlatSelect(new String[] { "Conservative", "Balanced", "Aggressive" }, 1);
	private final FlatSelect capitalPicker = new FlatSelect(
			new String[] { "My capital", "1M gp", "10M gp", "50M gp", "100M gp", "500M gp", "1B gp" },
			new String[] { "", "1000000", "10000000", "50000000", "100000000", "500000000", "1000000000" }, 0);
	private final FlatSelect accountPicker = new FlatSelect(
			new String[] { "All items", "Members", "Free-to-play" },
			new String[] { "all", "members", "f2p" }, 0);
	private final JLabel finderStatus = new JLabel("", SwingConstants.CENTER);
	private final JPanel finderList = listPanel();
	private final JButton linkButton = styledButton("Link account");
	private final JButton upsell = styledButton("Link to unlock members flips");
	private List<Flip> lastFlips = Collections.emptyList();
	private boolean promptShowing;

	// Repaint the shared palette before a panel is (re)built. Keeps Theme itself
	// package-private while giving the plugin a single entry point to switch mode.
	public static void applyTheme(boolean light) {
		Theme.apply(light ? Theme.Mode.LIGHT : Theme.Mode.DARK);
	}

	// THE clipped-right-edge fix. The sidebar scroll pane never shows a
	// horizontal bar and renders the view at its preferred width — so any row
	// whose text out-measured 225px (a long finder meta line was enough) pushed
	// the whole panel wider and the viewport cut the right edge off every card
	// and button. Pinning the reported width forces every row to lay out inside
	// the visible area and truncate its own text instead.
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(PANEL_WIDTH, super.getPreferredSize().height);
	}

	public FlipsPanel(Runnable onRefresh, Runnable onLink, Runnable onUnlink, Runnable onOpenMovers,
			Runnable onOpenWeb, ItemIconLoader iconLoader, IntConsumer onOpenItem, LongConsumer onNotFlip,
			LongConsumer onRestore) {
		this.iconLoader = iconLoader;
		this.onOpenItem = onOpenItem;
		this.onNotFlip = onNotFlip;
		this.onRestore = onRestore;
		// Row icons are inventory sprites (no network); the loader repaints as they decode.
		gainers = new MoversRotator(Theme.NUM_SMALL, Theme.UP, iconLoader::sprite, onOpenItem);
		losers = new MoversRotator(Theme.NUM_SMALL, Theme.DOWN, iconLoader::sprite, onOpenItem);

		setLayout(new BorderLayout());
		setBackground(Theme.SURFACE);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(Theme.SURFACE);

		column.add(buildHeader());
		column.add(strut(10));
		column.add(buildTabBar());
		column.add(strut(10));

		// One pane per tab, swapped by the tab bar. Finder is the default landing.
		// Each pane is top-anchored (topWrap): CardLayout sizes the content area to
		// the tallest pane, so a shorter pane must NOT let its cards stretch to fill
		// the slack — the extra space stays empty below instead.
		content.setBackground(Theme.SURFACE);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(topWrap(buildFinderPane(onRefresh)), "finder");
		content.add(topWrap(buildFlipsPane(onOpenWeb)), "flips");
		content.add(topWrap(buildMoversPane(onOpenMovers)), "movers");
		content.add(topWrap(buildHistoryPane()), "history");
		column.add(content);

		upsell.addActionListener(event -> onLink.run());
		linkButton.addActionListener(event -> onLink.run());
		unlinkLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		unlinkLink.setVisible(false);
		unlinkLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onUnlink.run();
			}
		});
		linkLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		linkLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onLink.run();
			}
		});
		scanPicker.setOnChange(onRefresh);
		riskPicker.setOnChange(onRefresh);
		capitalPicker.setOnChange(onRefresh);
		accountPicker.setOnChange(this::renderFlips);

		selectTab("finder");
		add(column, BorderLayout.NORTH);
		setLinked(true);
	}

	// ---- tabs & panes --------------------------------------------------------

	private JPanel pane() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(Theme.SURFACE);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	// Pin a pane's content to the top. CardLayout hands every pane the height of
	// the tallest one; a BorderLayout NORTH keeps this pane's cards at their
	// natural height and lets the leftover space fall empty below, instead of
	// BoxLayout stretching a card (the hero) to swallow it.
	private JPanel topWrap(JComponent pane) {
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(Theme.SURFACE);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.add(pane, BorderLayout.NORTH);
		return wrap;
	}

	private JPanel buildTabBar() {
		JPanel bar = new JPanel(new GridLayout(1, 0));
		bar.setBackground(Theme.SURFACE);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
		addTab(bar, "finder", "Finder");
		addTab(bar, "flips", "Flips");
		addTab(bar, "movers", "Movers");
		addTab(bar, "history", "History");
		return bar;
	}

	private void addTab(JPanel bar, String key, String label) {
		JLabel tab = new JLabel(label, SwingConstants.CENTER);
		// 11px bold: four tabs share 225px, and "Movers"/"History" truncate at
		// the full body size.
		tab.setFont(Theme.BODY_BOLD.deriveFont(11f));
		tab.setForeground(Theme.MUTED);
		tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tab.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				selectTab(key);
			}
		});
		tabButtons.put(key, tab);
		bar.add(tab);
	}

	void selectTab(String key) {
		activeTab = key;
		((CardLayout) content.getLayout()).show(content, key);
		for (Map.Entry<String, JLabel> entry : tabButtons.entrySet()) {
			boolean active = entry.getKey().equals(key);
			JLabel tab = entry.getValue();
			tab.setForeground(active ? Theme.INK : Theme.MUTED);
			// Active tab carries a green underline; padding compensates so the
			// baseline never shifts between states.
			tab.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, active ? 2 : 0, 0, Theme.UP),
					BorderFactory.createEmptyBorder(6, 4, active ? 6 : 8, 4)));
		}
		revalidate();
		repaint();
	}

	private JPanel buildFinderPane(Runnable onRefresh) {
		JPanel pane = pane();
		// Hint on the left, a compact refresh on the right — replaces the old
		// full-height gutter button that boxed the pickers in.
		JPanel hintRow = new JPanel(new BorderLayout(8, 0));
		hintRow.setBackground(Theme.SURFACE);
		hintRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel finderHint = text("Tap an item for full details ↗", Theme.MUTED, Theme.SMALL);
		hintRow.add(finderHint, BorderLayout.WEST);
		hintRow.add(flatButton("↻", onRefresh), BorderLayout.EAST);
		hintRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		pane.add(hintRow);
		pane.add(strut(8));
		pane.add(buildFinderBar());
		pane.add(strut(10));
		upsell.setAlignmentX(Component.LEFT_ALIGNMENT);
		upsell.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		upsell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		upsell.setVisible(false);
		pane.add(upsell);
		pane.add(strut(10));
		finderStatus.setForeground(Theme.MUTED);
		finderStatus.setFont(Theme.BODY);
		finderStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		pane.add(finderStatus);
		pane.add(wrapList(finderList));
		return pane;
	}

	private JPanel buildFlipsPane(Runnable onOpenWeb) {
		JPanel pane = pane();
		pane.add(buildStats());
		pane.add(strut(6));
		webHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		webHint.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		webHint.setVisible(false);
		webHint.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onOpenWeb.run();
			}
		});
		pane.add(webHint);
		pane.add(strut(8));

		offersSection.setLayout(new BorderLayout());
		offersSection.setBackground(Theme.SURFACE);
		offersSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		offersSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		offersSection.add(sectionHeader("Working offers", offersCount), BorderLayout.NORTH);
		offersSection.add(wrapList(offersList), BorderLayout.CENTER);
		offersSection.setVisible(false);
		pane.add(offersSection);

		activeSection.setLayout(new BorderLayout());
		activeSection.setBackground(Theme.SURFACE);
		activeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		activeSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		activeSection.add(sectionHeader("Active flips", activeCount), BorderLayout.NORTH);
		activeSection.add(wrapList(activeList), BorderLayout.CENTER);
		activeSection.setVisible(false);
		pane.add(activeSection);
		return pane;
	}

	private JPanel buildMoversPane(Runnable onOpenMovers) {
		JPanel pane = pane();
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
		buildMoverGroup(losersGroup, "Losers", losers, 10);
		JPanel moversBody = new JPanel();
		moversBody.setLayout(new BoxLayout(moversBody, BoxLayout.Y_AXIS));
		moversBody.setBackground(Theme.SURFACE);
		moversBody.setAlignmentX(Component.LEFT_ALIGNMENT);
		moversBody.add(gainersGroup);
		moversBody.add(losersGroup);
		moversSection.add(moversBody, BorderLayout.CENTER);
		pane.add(moversSection);
		return pane;
	}

	private JPanel buildHistoryPane() {
		JPanel pane = pane();
		JLabel blurb = text("Marked not a flip. Restore anytime.", Theme.MUTED, Theme.SMALL);
		blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
		pane.add(blurb);
		pane.add(strut(8));
		historySection.setLayout(new BorderLayout());
		historySection.setBackground(Theme.SURFACE);
		historySection.setAlignmentX(Component.LEFT_ALIGNMENT);
		historySection.add(sectionHeader("Archived", historyCount), BorderLayout.NORTH);
		historySection.add(wrapList(historyList), BorderLayout.CENTER);
		pane.add(historySection);
		return pane;
	}

	// ---- public updates (call on the EDT) ------------------------------------

	public String selectedScan() {
		return scanPicker.selectedValue();
	}

	public String selectedRisk() {
		return riskPicker.selectedValue().toLowerCase();
	}

	public Long selectedCapital() {
		String value = capitalPicker.selectedValue();
		return value.isEmpty() ? null : Long.valueOf(value);
	}

	public void setLinked(boolean linked) {
		this.linked = linked;
		linkStatus.setText(linked
				? "<html><span style='color:#2ec27e'>&#9679;</span> <span style='color:#9a9aa4'>Linked</span></html>"
				: "<html><span style='color:#9a9aa4'>Not linked</span></html>");
		unlinkLink.setVisible(linked);
		linkLink.setVisible(!linked);
		webHint.setVisible(linked);
		// History is account-only: hide its tab when unlinked, and bounce off it
		// if it was the active pane.
		JLabel historyTab = tabButtons.get("history");
		if (historyTab != null) {
			historyTab.setVisible(linked);
		}
		if (!linked && "history".equals(activeTab)) {
			selectTab("finder");
		}
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
		lastPositions = response.getPositions() != null
				? response.getPositions() : Collections.emptyList();
		renderActiveFlips();
	}

	private void renderActiveFlips() {
		activeCount.setText(Integer.toString(lastPositions.size()));
		activeList.removeAll();
		for (Position position : lastPositions) {
			addCard(activeList, activeFlipCard(position));
		}
		activeSection.setVisible(!lastPositions.isEmpty());
		revalidate();
		repaint();
	}

	// Package-private for the render harness; the card head's click lands here.
	void toggleFlip(long positionId) {
		if (!expandedFlips.remove(positionId)) {
			expandedFlips.add(positionId);
		}
		renderActiveFlips();
	}

	public void showHistory(PositionsResponse response) {
		List<Position> positions = response.getPositions() != null
				? response.getPositions() : Collections.emptyList();
		historyCount.setText(Integer.toString(positions.size()));
		historyList.removeAll();
		for (Position position : positions) {
			addCard(historyList, historyCard(position));
		}
		revalidate();
		repaint();
	}

	// Back to the zeros-and-dashes empty state; the positions fetch 401s once
	// unlinked, so it can never clear the previous account's numbers itself.
	public void clearAccountData() {
		showActiveFlips(PositionsResponse.builder().build());
	}

	public void showMovers(Movers movers) {
		List<MoverEntry> risers = movers != null ? movers.getRisers() : null;
		List<MoverEntry> fallers = movers != null ? movers.getFallers() : null;
		gainers.setEntries(risers);
		losers.setEntries(fallers);
		// Hide an empty group so a one-sided day leaves no dangling header.
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
		// Full-size ink heading: the old small muted caption disappeared next to
		// the rows it labels.
		JLabel heading = text(label, Theme.INK, Theme.BODY_BOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		group.add(heading);
		group.add(rotator);
	}

	public void showFlips(List<Flip> flips, boolean linked) {
		lastFlips = flips != null ? flips : Collections.emptyList();
		promptShowing = false;
		setLinked(linked);
		// Unlinked callers see the free (non-members) tier, so invite them to link
		// for the members-item flips rather than gating the panel behind an account.
		upsell.setVisible(!linked);
		renderFlips();
	}

	void renderFlips() {
		if (promptShowing) {
			return;
		}
		finderStatus.setText("");
		finderStatus.setVisible(false);
		finderList.removeAll();
		String account = accountPicker.selectedValue();
		List<Flip> shown = new ArrayList<>();
		for (Flip flip : lastFlips) {
			if ("members".equals(account) && !Boolean.TRUE.equals(flip.getMembers())) {
				continue;
			}
			if ("f2p".equals(account) && !Boolean.FALSE.equals(flip.getMembers())) {
				continue;
			}
			shown.add(flip);
		}
		for (Flip flip : shown) {
			addCard(finderList, finderCard(flip));
		}
		if (shown.isEmpty()) {
			finderStatus.setVisible(true);
			finderStatus.setText(lastFlips.isEmpty()
					? "No flips match your settings right now"
					: "No " + ("f2p".equals(account) ? "free-to-play" : "members") + " flips in this scan right now");
		}
		revalidate();
		repaint();
	}

	public void showStatus(String message) {
		promptShowing = true;
		upsell.setVisible(false);
		finderStatus.setVisible(true);
		finderStatus.setText(message);
		finderList.removeAll();
		revalidate();
		repaint();
	}

	public void showLinkPrompt() {
		promptShowing = true;
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
		promptShowing = true;
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
		// Link / Unlink are real buttons (bordered), not text links.
		styleHeaderButton(unlinkLink, Theme.MUTED, Theme.LINE_STRONG);
		styleHeaderButton(linkLink, Theme.INFO, Theme.INFO);
		JPanel status = new JPanel();
		status.setLayout(new BoxLayout(status, BoxLayout.X_AXIS));
		status.setBackground(Theme.SURFACE);
		status.add(linkStatus);
		status.add(Box.createHorizontalStrut(8));
		status.add(unlinkLink);
		status.add(linkLink);
		header.add(brand, BorderLayout.WEST);
		header.add(status, BorderLayout.EAST);
		return header;
	}

	private static void styleHeaderButton(JLabel label, java.awt.Color fg, java.awt.Color border) {
		label.setForeground(fg);
		label.setOpaque(false);
		label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(border, 1, true),
				BorderFactory.createEmptyBorder(3, 9, 3, 9)));
	}

	private JPanel buildStats() {
		JPanel stats = new JPanel();
		stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
		stats.setBackground(Theme.SURFACE);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Hero: lifetime profit, the number the panel exists to show. Leads the
		// same way the website's My Flips summary does.
		RoundedPanel hero = new RoundedPanel(10, Theme.RAISED, Theme.LINE);
		hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
		hero.setBorder(BorderFactory.createEmptyBorder(13, 14, 13, 14));
		hero.setAlignmentX(Component.LEFT_ALIGNMENT);
		hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		JLabel heroCaption = text("TOTAL PROFIT", Theme.MUTED, Theme.SECTION);
		heroCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
		allTimeValue.setFont(Theme.NUM_HERO);
		allTimeValue.setAlignmentX(Component.LEFT_ALIGNMENT);
		hero.add(heroCaption);
		hero.add(Box.createVerticalStrut(5));
		hero.add(allTimeValue);
		stats.add(hero);
		stats.add(Box.createVerticalStrut(9));

		// Four supporting tiles, each its own card — the clean, separated look
		// from the website rather than one dense block.
		JPanel grid = new JPanel(new GridLayout(2, 2, 9, 9));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		grid.add(statTile("TODAY", todayValue));
		grid.add(statTile("OPEN P/L", unrealizedValue));
		grid.add(statTile("WIN RATE", winValue));
		grid.add(statTile("AVG ROI", roiValue));
		stats.add(grid);
		return stats;
	}

	private JPanel statTile(String caption, JLabel value) {
		RoundedPanel tile = new RoundedPanel(8, Theme.RAISED, Theme.LINE);
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		tile.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
		JLabel label = text(caption, Theme.MUTED, Theme.SECTION);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		value.setFont(Theme.NUM_BOLD);
		value.setAlignmentX(Component.LEFT_ALIGNMENT);
		tile.add(label);
		tile.add(Box.createVerticalStrut(4));
		tile.add(value);
		return tile;
	}

	// Full-width pickers, the mock's field stack. The refresh control lives in
	// the hint row above, not in a tall gutter beside these.
	private JPanel buildFinderBar() {
		JPanel pickers = new JPanel();
		pickers.setLayout(new BoxLayout(pickers, BoxLayout.Y_AXIS));
		pickers.setBackground(Theme.SURFACE);
		pickers.setAlignmentX(Component.LEFT_ALIGNMENT);
		pickers.add(scanPicker);
		pickers.add(Box.createVerticalStrut(7));
		pickers.add(riskPicker);
		pickers.add(Box.createVerticalStrut(7));
		pickers.add(capitalPicker);
		pickers.add(Box.createVerticalStrut(7));
		pickers.add(accountPicker);
		return pickers;
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

	private static final long STUCK_OFFER_MINUTES = 30;

	private RoundedPanel offerCard(GeOffer offer, String name) {
		RoundedPanel card = new RoundedPanel(10, Theme.RAISED, Theme.LINE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));

		boolean buy = "buy".equals(offer.getSide());
		boolean full = offer.getQuantityTotal() > 0
				&& offer.getQuantityFilled() >= offer.getQuantityTotal();

		// Head: small icon, name + pending age (amber once an unfilled offer
		// looks stuck), slot number beneath. The bar and figures span the full
		// card width below, so nothing fights for room beside a tall icon.
		JPanel head = new JPanel(new BorderLayout(9, 0));
		head.setOpaque(false);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.add(icon(offer.getItemId(), name, 26), BorderLayout.WEST);
		JPanel nameCol = new JPanel();
		nameCol.setOpaque(false);
		nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(nameLabel(name), BorderLayout.CENTER);
		Long ageMinutes = offerAgeMinutes(offer.getOccurredAt());
		if (ageMinutes != null) {
			boolean stuck = !full && ageMinutes >= STUCK_OFFER_MINUTES;
			top.add(text(formatAge(ageMinutes), stuck ? Theme.AMBER : Theme.MUTED, Theme.NUM_TINY), BorderLayout.EAST);
		}
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameCol.add(top);
		nameCol.add(Box.createVerticalStrut(2));
		// Which of the 8 GE slots this offer occupies (1-based for humans).
		JLabel slot = text("Slot " + (offer.getSlot() + 1), Theme.MUTED, Theme.SMALL);
		slot.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameCol.add(slot);
		head.add(nameCol, BorderLayout.CENTER);
		card.add(head);
		card.add(Box.createVerticalStrut(8));

		double fraction = offer.getQuantityTotal() > 0
				? (double) offer.getQuantityFilled() / offer.getQuantityTotal() : 0;
		// A completed offer (fully bought OR fully sold) reads as done: the pill and
		// the whole bar go green. In flight, buys are blue and sells amber.
		java.awt.Color accent = full ? Theme.UP : (buy ? Theme.INFO : Theme.AMBER);
		JPanel barRow = new JPanel(new BorderLayout(7, 0));
		barRow.setOpaque(false);
		barRow.add(solidPill(full ? "DONE" : (buy ? "BUY" : "SELL"), accent), BorderLayout.WEST);
		FillBar progress = new FillBar(fraction, accent);
		progress.setPreferredSize(new Dimension(10, 6));
		barRow.add(progress, BorderLayout.CENTER);
		barRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		barRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		card.add(barRow);
		card.add(Box.createVerticalStrut(6));

		// Filled / total on the left; the price you set and the offer's total
		// value on the right, prominent — the number the flipper cares about.
		long value = (long) offer.getPriceEach() * offer.getQuantityTotal();
		addLeft(card, detailRow(GP.format(offer.getQuantityFilled()) + " / " + GP.format(offer.getQuantityTotal()),
				"@ " + GP.format(offer.getPriceEach()) + " · " + shortGp(value), Theme.INK));

		clickable(card, offer.getItemId());
		return card;
	}

	// Minutes since the offer's last state change (placement, for an offer that
	// has not filled). Null when the timestamp is missing or unparseable.
	private static Long offerAgeMinutes(String occurredAt) {
		if (occurredAt == null) {
			return null;
		}
		try {
			long minutes = Duration.between(Instant.parse(occurredAt), Instant.now()).toMinutes();
			return minutes < 0 ? 0 : minutes;
		} catch (RuntimeException unparseable) {
			return null;
		}
	}

	private static String formatAge(long minutes) {
		if (minutes < 60) {
			return minutes + "m";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			long rem = minutes % 60;
			return rem == 0 ? hours + "h" : hours + "h " + rem + "m";
		}
		return (hours / 24) + "d";
	}

	// The mock's finder card: a compact head row (small icon, name + tier tag,
	// entry meta) and then everything else — pills, prices, stats, fill times —
	// spanning the full card width instead of being squeezed right of a big
	// vertically-centered icon.
	private RoundedPanel finderCard(Flip flip) {
		RoundedPanel card = new RoundedPanel(10, Theme.RAISED, Theme.LINE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));

		JPanel head = new JPanel(new BorderLayout(9, 0));
		head.setOpaque(false);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.add(icon(flip.getItemId(), flip.getName(), 26), BorderLayout.WEST);
		JLabel finderName = nameLabel(flip.getName());
		shrinkToFit(finderName, CONTENT_WIDTH - 26 - 9);
		head.add(finderName, BorderLayout.CENTER);
		card.add(head);
		// Tier + margin get their own full-width line under the head: beside the
		// icon the ~148px column ellipsized "Members · 14.7% below target"
		// mid-word at any readable size. Shrink floor + tooltip as backstops.
		String tier = flip.getMembers() == null ? null : (flip.getMembers() ? "Members" : "F2P");
		String margin = marginLabel(flip);
		String meta = tier == null ? margin : (margin == null ? tier : tier + " · " + margin);
		if (meta != null) {
			card.add(Box.createVerticalStrut(3));
			JLabel metaLabel = text(meta, Theme.MUTED, Theme.SMALL);
			shrinkToFit(metaLabel, CONTENT_WIDTH, 9.5f);
			metaLabel.setToolTipText(meta);
			addLeft(card, metaLabel);
		}

		Pill demand = demandPill(flip.getDemandTrend());
		if (demand != null) {
			card.add(Box.createVerticalStrut(8));
			addLeft(card, demand);
		}

		card.add(Box.createVerticalStrut(9));
		addLeft(card, detailRow("Buy " + GP.format(flip.getQuantity()), "@ " + GP.format(flip.getBuyPrice()), Theme.INK));
		addLeft(card, detailRow("Sell", "@ " + GP.format(flip.getTargetSellPrice()), Theme.INK));
		addLeft(card, text("+" + shortGp(flip.getProfitPerItem()) + " / ea after tax", Theme.UP, Theme.NUM_SMALL));

		card.add(Box.createVerticalStrut(9));
		addLeft(card, divider());
		card.add(Box.createVerticalStrut(9));
		JPanel stats = new JPanel(new BorderLayout());
		stats.setOpaque(false);
		stats.add(statMini("Profit", text("+" + shortGp(flip.getTotalProfit()), Theme.UP, Theme.NUM_LG),
				Component.LEFT_ALIGNMENT), BorderLayout.WEST);
		stats.add(statMini("ROI / day", text(trimNum(flip.getRoiPerDay()) + "%", Theme.INK, Theme.NUM_BOLD),
				Component.RIGHT_ALIGNMENT), BorderLayout.EAST);
		addLeft(card, stats);

		card.add(Box.createVerticalStrut(9));
		addLeft(card, labeledValue("Time to buy", fillTime(flip.getBuyFill())));
		card.add(Box.createVerticalStrut(3));
		addLeft(card, labeledValue("Time to sell", fillTime(flip.getSellFill())));
		if (flip.getHorizon() != null) {
			card.add(Box.createVerticalStrut(3));
			addLeft(card, labeledValue("Hold up to", flip.getHorizon().getRecommendedDays() + "d"));
		}

		clickable(card, flip.getItemId());
		// Explicit affordance for the same action as the card click, added AFTER
		// clickable() so it stays its own hit target (the mock's primary button).
		JPanel actions = new JPanel(new BorderLayout());
		actions.setOpaque(false);
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		actions.add(actionButton("Item details ↗", true,
				() -> onOpenItem.accept(flip.getItemId())), BorderLayout.EAST);
		card.add(actions);
		return card;
	}

	private JPanel labeledValue(String label, String value) {
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		row.add(text(label, Theme.MUTED, Theme.SMALL), BorderLayout.WEST);
		JLabel number = text(value, Theme.INK, Theme.NUM_SMALL);
		number.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(number, BorderLayout.EAST);
		return row;
	}

	private static void addLeft(JPanel body, JComponent child) {
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(child);
	}

	// Step the font down (floor 11px) until the text fits: a whole name one
	// notch smaller beats "Extended ant…" at full size. Ellipsis only after.
	private static void shrinkToFit(JLabel label, int available) {
		shrinkToFit(label, available, 11f);
	}

	private static void shrinkToFit(JLabel label, int available, float floor) {
		java.awt.Font base = label.getFont();
		for (float size = base.getSize2D(); size >= floor; size -= 0.5f) {
			label.setFont(base.deriveFont(size));
			if (label.getPreferredSize().width <= available) {
				return;
			}
		}
	}

	private static String marginLabel(Flip flip) {
		if ("fast".equals(flip.getStrategy()) && flip.getSpreadPct() != null) {
			return trimNum(flip.getSpreadPct()) + "% margin";
		}
		if (flip.getEntryDiscountPct() != null) {
			String baseline = "value".equals(flip.getStrategy()) ? "% below target" : "% below typical";
			return trimNum(flip.getEntryDiscountPct()) + baseline;
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
		JLabel caption = text(label.toUpperCase(), Theme.MUTED, Theme.SMALL);
		caption.setAlignmentX(align);
		value.setAlignmentX(align);
		col.add(caption);
		col.add(Box.createVerticalStrut(2));
		col.add(value);
		return col;
	}

	// The mock's expandable flip card: the head (icon, name, qty @ price, P&L,
	// chevron) toggles a detail section with labeled rows and the two actions —
	// "View item details" (opens the web page, the head no longer does) and
	// "Not a flip". Collapsed cards stay one clean line.
	private RoundedPanel activeFlipCard(Position position) {
		boolean expanded = expandedFlips.contains(position.getId());
		RoundedPanel card = new RoundedPanel(10, Theme.RAISED, Theme.LINE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));

		// Two lines instead of one four-column row: at RuneLite's 225px panel
		// width the name can't share a line with the P&L without truncating, so
		// line 1 is icon + name (+SELL) + chevron, and line 2 pairs qty @ price
		// with the P&L. Names render whole.
		JPanel head = new JPanel();
		head.setOpaque(false);
		head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
		head.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel line1 = new JPanel(new BorderLayout(8, 0));
		line1.setOpaque(false);
		line1.setAlignmentX(Component.LEFT_ALIGNMENT);
		line1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		line1.add(icon(position.getItemId(), position.getName(), 24), BorderLayout.WEST);
		// The name owns line 1 outright — the SELL state rides line 2 with the
		// P&L, so even long names render whole (with a shrink step before any
		// ellipsis).
		JLabel flipName = nameLabel(position.getName());
		shrinkToFit(flipName, CONTENT_WIDTH - 24 - 8 - 20);
		line1.add(flipName, BorderLayout.CENTER);
		line1.add(text(expanded ? "▲" : "▼", Theme.MUTED, Theme.NUM_TINY), BorderLayout.EAST);
		head.add(line1);
		head.add(Box.createVerticalStrut(4));

		// Glue row, not WEST/EAST: when the two sides would collide, the sub
		// compresses and ellipsizes instead of the P&L stamping over it.
		JPanel line2 = new JPanel();
		line2.setOpaque(false);
		line2.setLayout(new BoxLayout(line2, BoxLayout.X_AXIS));
		line2.setAlignmentX(Component.LEFT_ALIGNMENT);
		line2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		JLabel sub = text(GP.format(position.getQuantity()) + " @ " + GP.format(position.getBuyPrice()),
				Theme.MUTED, Theme.NUM_SMALL);
		sub.setMinimumSize(new Dimension(40, sub.getPreferredSize().height));
		sub.setMaximumSize(sub.getPreferredSize());
		line2.add(sub);
		line2.add(Box.createHorizontalGlue());
		if (isSell(position.getPhase())) {
			// This flip's signal is "sell now" — the solid amber chip carries it.
			line2.add(solidPill("SELL", Theme.AMBER));
			line2.add(Box.createHorizontalStrut(6));
		}
		// Headline number is the flip's TOTAL profit (realized + unrealized), the
		// same figure the expanded card leads with — not just the unrealized leg.
		Long unrealized = position.getUnrealizedProfit();
		Long realized = position.getRealizedProfit();
		Long profit = unrealized == null && realized == null ? null
				: (unrealized == null ? 0 : unrealized) + (realized == null ? 0 : realized);
		if (profit == null) {
			line2.add(text("—", Theme.MUTED, Theme.NUM_BOLD));
		} else {
			line2.add(text(pnlText(profit), profit >= 0 ? Theme.UP : Theme.DOWN, Theme.NUM_BOLD));
			if (position.getRoi() != null) {
				line2.add(Box.createHorizontalStrut(5));
				line2.add(text(signedPct(position.getRoi()), Theme.MUTED, Theme.NUM_SMALL));
			}
		}
		head.add(line2);
		card.add(head);

		// The head is the expand toggle (with the card's hover highlight); the
		// item page moved to its explicit button below.
		installRecursively(head, new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				toggleFlip(position.getId());
			}

			@Override
			public void mouseEntered(MouseEvent event) {
				card.setFill(Theme.HOVER);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				card.setFill(Theme.RAISED);
			}
		});

		if (expanded) {
			card.add(Box.createVerticalStrut(9));
			addLeft(card, divider());
			card.add(Box.createVerticalStrut(8));
			// The mock's P&L block: Total profit leads, the partial-fill split
			// (unrealized vs realized) only appears once something has sold, then
			// the per-item average and ROI.
			addLeft(card, detailRow("Total profit",
					profit == null ? "—" : pnlText(profit),
					profit == null ? Theme.MUTED : (profit >= 0 ? Theme.UP : Theme.DOWN)));
			boolean hasSold = position.getSoldQty() != null && position.getSoldQty() > 0;
			if (hasSold && unrealized != null) {
				addLeft(card, detailRow("Unrealized P/L", pnlText(unrealized),
						unrealized >= 0 ? Theme.UP : Theme.DOWN));
			}
			if (hasSold && realized != null) {
				addLeft(card, detailRow("Realized so far", pnlText(realized),
						realized >= 0 ? Theme.UP : Theme.DOWN));
			}
			if (profit != null && position.getQuantity() > 0) {
				long perItem = profit / position.getQuantity();
				addLeft(card, detailRow("Avg profit / ea", pnlText(perItem),
						perItem >= 0 ? Theme.UP : Theme.DOWN));
			}
			if (position.getRoi() != null) {
				addLeft(card, detailRow("Avg ROI", signedPct(position.getRoi()), Theme.INK));
			}
			card.add(Box.createVerticalStrut(6));
			addLeft(card, divider());
			card.add(Box.createVerticalStrut(6));
			addLeft(card, detailRow("Bought",
					GP.format(position.getQuantity()) + " @ " + GP.format(position.getBuyPrice()), Theme.INK));
			addLeft(card, detailRow("Sold",
					hasSold
							? GP.format(position.getSoldQty())
									+ (position.getSoldAvgPrice() != null ? " @ " + GP.format(position.getSoldAvgPrice()) : "")
							: "— none yet",
					hasSold ? Theme.INK : Theme.MUTED));
			addLeft(card, detailRow("Cost", shortGp(position.getBuyPrice() * position.getQuantity()), Theme.INK));

			// BoxLayout + glue, not WEST/EAST: if font metrics ever run wider than
			// the row, the buttons compress and their labels ellipsize INSIDE their
			// rounded borders instead of the right button clipping at the card edge.
			JPanel actions = new JPanel();
			actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
			actions.setOpaque(false);
			actions.setAlignmentX(Component.LEFT_ALIGNMENT);
			actions.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
			actions.add(actionButton("Item details ↗", true,
					() -> onOpenItem.accept(position.getItemId())));
			actions.add(Box.createHorizontalGlue());
			actions.add(actionButton("Not a flip", false,
					() -> onNotFlip.accept(position.getId())));
			card.add(actions);
		}
		return card;
	}

	private JPanel detailRow(String label, String value, java.awt.Color valueColor) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
		row.setPreferredSize(new Dimension(10, 21));
		row.add(text(label, Theme.MUTED, Theme.SMALL), BorderLayout.WEST);
		JLabel number = text(value, valueColor, Theme.NUM_SMALL);
		number.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(number, BorderLayout.EAST);
		return row;
	}

	// A real button with its own listener, never inheriting a parent card's
	// click-through. Solid fills, NO outline: a 1px border stroke sits on the
	// component's outermost pixel row, and at fractional Windows DPI scales the
	// live client's partial repaints (hover, tooltips, polls) round that row out
	// of the dirty region — the top edge kept vanishing. A solid chip has no
	// hairline to lose. Primary wears the theme's near-white accent (the
	// website's primary button), not a semantic hue; ghost is a quiet inset.
	private RoundedPanel actionButton(String label, boolean primary, Runnable onClick) {
		java.awt.Color rest = primary ? Theme.INK : Theme.TRACK;
		java.awt.Color hover = primary ? Theme.WHITE : Theme.HOVER;
		RoundedPanel button = new RoundedPanel(7, rest, null);
		button.setLayout(new BorderLayout());
		// 9px sides: two buttons must share a 203px row without touching.
		button.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
		JLabel text = text(label, primary ? Theme.SURFACE : Theme.MUTED, primary ? Theme.SMALL_BOLD : Theme.SMALL);
		text.setHorizontalAlignment(SwingConstants.CENTER);
		// Small minimum + pref-capped maximum: in a BoxLayout row the button can
		// compress (label ellipsizes inside the border) but never stretches.
		text.setMinimumSize(new Dimension(24, text.getPreferredSize().height));
		button.add(text, BorderLayout.CENTER);
		button.setMaximumSize(new Dimension(
				button.getPreferredSize().width, button.getPreferredSize().height));
		installRecursively(button, new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent event) {
				button.setFill(hover);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				button.setFill(rest);
			}
		});
		return button;
	}

	// A History-tab card, detailed like the mock: quantity, bought at, sold at
	// ("— not sold" when the item never moved), spent, and the restore action.
	// The whole card still opens item details.
	private RoundedPanel historyCard(Position position) {
		RoundedPanel card = new RoundedPanel(10, Theme.RAISED, Theme.LINE);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));

		JPanel head = new JPanel(new BorderLayout(9, 0));
		head.setOpaque(false);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.add(icon(position.getItemId(), position.getName(), 26), BorderLayout.WEST);
		JPanel nameCol = new JPanel();
		nameCol.setOpaque(false);
		nameCol.setLayout(new BoxLayout(nameCol, BoxLayout.Y_AXIS));
		JLabel name = nameLabel(position.getName());
		shrinkToFit(name, CONTENT_WIDTH - 26 - 9);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameCol.add(name);
		nameCol.add(Box.createVerticalStrut(2));
		JLabel sub = text("Archived", Theme.MUTED, Theme.SMALL);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameCol.add(sub);
		head.add(nameCol, BorderLayout.CENTER);
		card.add(head);

		card.add(Box.createVerticalStrut(8));
		addLeft(card, detailRow("Quantity", GP.format(position.getQuantity()), Theme.INK));
		addLeft(card, detailRow("Bought at", GP.format(position.getBuyPrice()), Theme.INK));
		boolean sold = position.getSoldQty() != null && position.getSoldQty() > 0
				&& position.getSoldAvgPrice() != null;
		addLeft(card, detailRow("Sold at",
				sold ? GP.format(position.getSoldAvgPrice()) : "— not sold",
				sold ? Theme.INK : Theme.MUTED));
		addLeft(card, detailRow("Spent", shortGp(position.getBuyPrice() * position.getQuantity()), Theme.INK));

		clickable(card, position.getItemId());
		// Added AFTER clickable() so the restore button keeps its own listener.
		JPanel actions = new JPanel(new BorderLayout());
		actions.setOpaque(false);
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));
		actions.add(actionButton("Make it a flip", true,
				() -> onRestore.accept(position.getId())), BorderLayout.EAST);
		card.add(actions);
		return card;
	}

	// ---- small helpers -------------------------------------------------------

	// Card-head icons are small (26px, the mock's .ic) so the name keeps the
	// width; the finder's old 36px icon floating mid-card was the main "looks
	// amateur" offender.
	private JLabel icon(int itemId, String name, int size) {
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(size + 2, size));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		iconLoader.load(itemId, name, label, size);
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

	// Borderless like the mock's .dpill — and like the buttons, no hairline for
	// fractional-DPI partial repaints to shave off.
	private Pill pill(String label, java.awt.Color hue) {
		return new Pill(label, hue, Theme.soft(hue), null);
	}

	// The mock's .pill: a solid accent chip with dark ink text and no border —
	// the BUY/SELL/DONE state markers. Translucent pill() stays for the softer
	// demand tags.
	private Pill solidPill(String label, java.awt.Color hue) {
		Pill chip = new Pill(label, Theme.PILL_INK, hue, null);
		chip.setFont(Theme.PILL);
		return chip;
	}

	private JPanel sectionHeader(String title, JLabel count) {
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
		header.setBackground(Theme.SURFACE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(2, 0, 9, 0));

		// Lighter (FAINT) with a touch of tracking reads calmer than a bold MUTED
		// caption, closer to the website's section labels.
		JLabel titleLabel = text(title.toUpperCase(), Theme.MUTED, Theme.SECTION);
		header.add(titleLabel);
		header.add(Box.createHorizontalStrut(9));
		if (count != null) {
			count.setForeground(Theme.MUTED);
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
