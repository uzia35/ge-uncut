package app.geuncut.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import app.geuncut.dto.ItemPrice;
import app.geuncut.tracker.PriceHint;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.ItemChartData.Range;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ImageUtil;

public class OfferPriceOverlay extends Overlay {
	private static final Color LOGO = new Color(0x4A9EFF);
	private static final Color NAME = new Color(0xF0F0F0);
	private static final Color LABEL = new Color(0xB8BDC2);
	private static final Color VALUE = Color.WHITE;
	private static final Color AGO = new Color(0xD7DCE1);
	private static final Color BUTTON_FILL = new Color(0x3A, 0x41, 0x4A, 235);
	private static final Color BUTTON_FILL_HOVER = new Color(0x4A, 0x9E, 0xFF, 90);
	private static final Color BUTTON_BORDER = new Color(0x60, 0x67, 0x70);
	private static final int PAD = 6;
	private static final int LOGO_SIZE = 16;

	private final BufferedImage logo = ImageUtil.loadImageResource(OfferPriceOverlay.class, "/geuncut_icon.png");
	private volatile ItemPrice price;
	private volatile String itemName;
	private volatile BufferedImage icon;
	private volatile Rectangle detailsBounds;
	private volatile boolean detailsHover;
	private final ItemHistoryChart chart = new ItemHistoryChart();
	private final Map<Range, Rectangle> rangeBounds = new EnumMap<>(Range.class);
	private Range range = Range.MONTH;
	private Snapshot history;
	private int itemId = -1;
	private Rectangle retryBounds;
	private Rectangle websiteBounds;
	private boolean detailsOpen;
	private boolean expanded;
	private Rectangle expandBounds;
	private boolean dismissed;
	private Rectangle closeBounds;
	private final ItemVolumeDetails volumeDetails = new ItemVolumeDetails();
	private Point mouse;
	private boolean showChart = true;

	public OfferPriceOverlay() {
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public synchronized void show(int itemId, ItemPrice price, String itemName, BufferedImage icon, Snapshot history) {
		show(itemId, price, itemName, icon, history, true);
	}

	public synchronized void show(int itemId, ItemPrice price, String itemName, BufferedImage icon, Snapshot history,
			boolean showChart) {
		if (this.itemId != itemId) {
			dismissed = false;
			closeBounds = null;
			range = Range.MONTH;
			rangeBounds.clear();
			detailsBounds = null;
			retryBounds = null;
			mouse = null;
			websiteBounds = null;
			detailsOpen = false;
			expanded = false;
			expandBounds = null;
		}
		this.itemId = itemId;
		this.price = price;
		this.itemName = itemName;
		this.icon = icon;
		this.history = history != null && history.getItemId() == itemId ? history : null;
		this.showChart = showChart;
		if (!showChart) {
			closeBounds = null;
			rangeBounds.clear();
			retryBounds = null;
			websiteBounds = null;
			detailsOpen = false;
			expanded = false;
			expandBounds = null;
		}
	}

	public synchronized void clear() {
		this.dismissed = false;
		this.closeBounds = null;
		this.itemId = -1;
		this.history = null;
		this.rangeBounds.clear();
		this.retryBounds = null;
		this.websiteBounds = null;
		this.detailsOpen = false;
		this.expanded = false;
		this.expandBounds = null;
		this.mouse = null;
		this.price = null;
		this.itemName = null;
		this.icon = null;
		this.detailsBounds = null;
		this.detailsHover = false;
	}

	public synchronized boolean isDetailsClick(Point canvasPoint) {
		Rectangle local = detailsBounds;
		Rectangle bounds = getBounds();
		if (local == null || bounds == null || itemId <= 0) {
			return false;
		}
		return canvasPoint.x >= bounds.x + local.x && canvasPoint.x <= bounds.x + local.x + local.width
				&& canvasPoint.y >= bounds.y + local.y && canvasPoint.y <= bounds.y + local.y + local.height;
	}

	public synchronized boolean handleChartClick(Point point, IntConsumer onRetry) {
		if (itemId <= 0 || dismissed) { return false; }
		Point local = localPoint(point);
		if (showChart && closeBounds != null && closeBounds.contains(local)) {
			int selectedItemId = itemId;
			clear();
			itemId = selectedItemId;
			dismissed = true;
			return true;
		}
		if (showChart && expandBounds != null && expandBounds.contains(local)) {
			expanded = detailsOpen || !expanded;
			detailsOpen = false;
			rangeBounds.clear();
			detailsBounds = null;
			websiteBounds = null;
			expandBounds = null;
			retryBounds = null;
			mouse = null;
			return true;
		}
		if (showChart && detailsBounds != null && detailsBounds.contains(local)) {
			detailsOpen = !detailsOpen;
			rangeBounds.clear();
			retryBounds = null;
			websiteBounds = null;
			mouse = null;
			return true;
		}
		for (Map.Entry<Range, Rectangle> entry : rangeBounds.entrySet()) {
			if (entry.getValue().contains(local)) {
				range = entry.getKey();
				return true;
			}
		}
		if (retryBounds != null && retryBounds.contains(local)
				&& history != null && history.getStatus() == Status.ERROR) {
			onRetry.accept(itemId);
			return true;
		}
		return false;
	}

	/** The compact price prompt retains its website action; chart Details opens locally. */
	public synchronized boolean isWebsiteClick(Point point) {
		return !showChart ? isDetailsClick(point) : itemId > 0 && detailsOpen
				&& websiteBounds != null && websiteBounds.contains(localPoint(point));
	}

	public synchronized void setMouse(Point point) {
		mouse = point == null ? null : new Point(point);
	}

	/** Dismissal belongs to this selection, not a persistent setting or another item. */
	public synchronized boolean isDismissed(int selectedItemId) {
		return dismissed && itemId == selectedItemId;
	}

	private Point localPoint(Point point) {
		Rectangle bounds = getBounds();
		return new Point(point.x - bounds.x, point.y - bounds.y);
	}

	public void setDetailsHover(boolean hover) {
		this.detailsHover = hover;
	}

	@Override
	public synchronized Dimension render(Graphics2D graphics) {
		if (itemId <= 0 || dismissed) { return null; }
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			return showChart ? renderSelectedItem(g) : renderPricesOnly(g);
		} finally { g.dispose(); }
	}

	private Dimension renderSelectedItem(Graphics2D g) {
		// Reuse the production popup's small font, padding, icon column and three text rows.
		ItemHistoryChart.prepare(g);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int lineH = metrics.getHeight();
		int ascent = metrics.getAscent();
		int iconW = icon != null ? icon.getWidth() : 36;
		int iconH = icon != null ? icon.getHeight() : 32;
		int headerH = Math.max(LOGO_SIZE, lineH);
		int contentTop = PAD + headerH + 6;
		int textX = PAD + iconW + 12;
		int valueX = textX + metrics.stringWidth("Sell") + 10;
		int contentH = Math.max(iconH, lineH * 3);
		int buttonY = contentTop + contentH + 6;
		int buttonH = lineH + 9;
		int summaryHeight = buttonY + buttonH + PAD;
		Instant now = Instant.now();
		String name = itemName != null && !itemName.isEmpty() ? itemName : "GE Uncut";
		String buyValue = summaryQuote(price, true, now);
		String sellValue = summaryQuote(price, false, now);
		ItemChartData.Point volumePoint = ItemVolumeDetails.latest(history, range);
		String[] volumes = topVolumeValues(volumePoint, history == null ? Status.LOADING : history.getStatus());
		for (int i = 0; i < volumes.length; i++) { volumes[i] = volumes[i].replace(" items", ""); }
		int volumeX = Math.max(textX + Math.min(180, metrics.stringWidth(name)),
				valueX + Math.max(metrics.stringWidth(buyValue), metrics.stringWidth(sellValue))) + 10;
		int volumeWidth = Math.max(metrics.stringWidth("Volume"),
				Math.max(metrics.stringWidth(volumes[0]), metrics.stringWidth(volumes[1])));
		// Keep short, cheap items wide enough for the same accordion controls and chart.
		int width = Math.max(232, volumeX + volumeWidth + PAD);
		int rangeY = summaryHeight + 4;
		Rectangle chartArea = new Rectangle(PAD, rangeY + 26, width - 2 * PAD, 154);
		int height = expanded || detailsOpen ? chartArea.y + chartArea.height + PAD : summaryHeight;
		g.setColor(new Color(20, 20, 22, 220));
		g.fillRoundRect(0, 0, width, height, 7, 7);
		g.setColor(new Color(90, 96, 108, 180));
		g.drawRoundRect(0, 0, width - 1, height - 1, 7, 7);
		if (logo != null) { g.drawImage(logo, PAD, PAD + (headerH - LOGO_SIZE) / 2, LOGO_SIZE, LOGO_SIZE, null); }
		text(g, "GE Uncut", PAD + LOGO_SIZE + 5, PAD + (headerH - lineH) / 2 + ascent, LOGO);
		closeBounds = new Rectangle(width - PAD - 14, PAD, 14, 14);
		button(g, closeBounds, "×", false, ItemHistoryChart.FONT.deriveFont(12f));
		g.setFont(FontManager.getRunescapeSmallFont());
		if (icon != null) { g.drawImage(icon, PAD, contentTop + (contentH - iconH) / 2, null); }
		int nameBaseline = contentTop + (contentH - lineH * 3) / 2 + ascent;
		text(g, fit(name, metrics, volumeX - textX - 10), textX, nameBaseline, NAME);
		text(g, "Buy", textX, nameBaseline + lineH, ItemHistoryChart.BUY);
		text(g, "Sell", textX, nameBaseline + lineH * 2, ItemHistoryChart.SELL);
		value(g, buyValue, valueX, nameBaseline + lineH);
		value(g, sellValue, valueX, nameBaseline + lineH * 2);
		text(g, "Volume", volumeX, nameBaseline, LABEL);
		text(g, volumes[0], width - PAD - metrics.stringWidth(volumes[0]), nameBaseline + lineH, VALUE);
		text(g, volumes[1], width - PAD - metrics.stringWidth(volumes[1]), nameBaseline + lineH * 2, VALUE);
		int buttonWidth = (width - 2 * PAD - 6) / 2;
		expandBounds = new Rectangle(PAD, buttonY, buttonWidth, buttonH);
		detailsBounds = new Rectangle(width - PAD - buttonWidth, buttonY, buttonWidth, buttonH);
		button(g, expandBounds, expanded && !detailsOpen ? "Hide chart ∧" : "Show chart ∨",
				expanded && !detailsOpen, FontManager.getRunescapeSmallFont());
		button(g, detailsBounds, detailsOpen ? "Close details" : "Item details ›",
				detailsOpen, FontManager.getRunescapeSmallFont());
		rangeBounds.clear();
		retryBounds = null;
		websiteBounds = null;
		if (!expanded && !detailsOpen) { return new Dimension(width, height); }
		g.setFont(ItemHistoryChart.FONT.deriveFont(java.awt.Font.BOLD, 11f));
		ItemHistoryChart.label(g, detailsOpen ? "Item details" : "History", PAD, rangeY + 15, ItemHistoryChart.INK);
		int rangeWidth = 128;
		int rangeX = width - PAD - rangeWidth;
		if (!detailsOpen) {
			for (Range option : Range.values()) {
				Rectangle bounds = new Rectangle(rangeX + option.ordinal() * rangeWidth / 4, rangeY, rangeWidth / 4 - 2, 22);
				rangeBounds.put(option, bounds);
				button(g, bounds, option.label, option == range);
			}
		}
		if (detailsOpen) { volumeDetails.render(g, chartArea, history, range); }
		else { chart.render(g, chartArea, history, range, mouse == null ? null : localPoint(mouse)); }
		if (history != null && history.getStatus() == Status.ERROR) {
			retryBounds = new Rectangle(width / 2 - 50, chartArea.y + 85, 100, 23);
			button(g, retryBounds, "Retry", false);
		}
		websiteBounds = detailsOpen ? new Rectangle(width - PAD - 114, rangeY, 114, 22) : null;
		if (websiteBounds != null) { button(g, websiteBounds, "Open website ›", false); }
		return new Dimension(width, height);
	}

	static String summaryQuote(ItemPrice price, boolean buy, Instant now) {
		Long amount = price == null ? null : buy ? price.getBuyPrice() : price.getSellPrice();
		return (amount == null ? "n/a" : String.format(Locale.ENGLISH, "%,d gp", amount))
				+ "   " + quoteAge(price, buy, now);
	}

	static String quoteAge(ItemPrice price, boolean buy, Instant now) {
		if (price == null || (buy ? price.getBuyPrice() : price.getSellPrice()) == null) { return "Time unknown"; }
		String age = PriceHint.ago(buy ? price.getLowTime() : price.getHighTime(), now);
		return "?".equals(age) ? "Time unknown" : age;
	}

	static String[] topVolumeValues(ItemChartData.Point point, Status status) {
		if (status == Status.LOADING || status == Status.IDLE) { return new String[] {"Loading", "Loading"}; }
		if (status == Status.ERROR) { return new String[] {"Unavailable", "Unavailable"}; }
		return new String[] {ItemVolumeDetails.quantity(point == null ? null : point.getBuyVolume()),
				ItemVolumeDetails.quantity(point == null ? null : point.getSellVolume())};
	}

	private void button(Graphics2D g, Rectangle bounds, String label, boolean active) {
		button(g, bounds, label, active, ItemHistoryChart.FONT);
	}

	private void button(Graphics2D g, Rectangle bounds, String label, boolean active, java.awt.Font font) {
		boolean hovered = mouse != null && bounds.contains(localPoint(mouse));
		g.setColor(hovered ? new Color(54, 93, 132, 245) : active ? new Color(37, 65, 93, 240) : new Color(44, 49, 58, 220));
		g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
		g.setColor(hovered || active ? ItemHistoryChart.BUY : new Color(99, 110, 124, 210));
		g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 6, 6);
		g.setFont(font);
		ItemHistoryChart.label(g, label, bounds.x + (bounds.width - g.getFontMetrics().stringWidth(label)) / 2,
				bounds.y + (bounds.height + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2, ItemHistoryChart.INK);
	}

	/** Retain the existing compact price-prompt presentation when charts are disabled. */
	private Dimension renderPricesOnly(Graphics2D graphics) {
		Instant now = Instant.now();
		String name = itemName != null && !itemName.isEmpty() ? itemName : "GE Uncut";
		String buyValue = price == null ? "-" : quote(price.getBuyPrice(), price.getLowTime(), now);
		String sellValue = price == null ? "-" : quote(price.getSellPrice(), price.getHighTime(), now);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics text = graphics.getFontMetrics();
		int lineH = text.getHeight();
		int ascent = text.getAscent();
		int iconW = icon != null ? icon.getWidth() : 36;
		int iconH = icon != null ? icon.getHeight() : 32;
		int headerH = Math.max(LOGO_SIZE, lineH);
		int contentTop = PAD + headerH + 6;
		int textX = PAD + iconW + 12;
		int valueX = textX + text.stringWidth("Sell") + 10;
		int textBlockH = lineH * 3;
		int contentH = Math.max(iconH, textBlockH);
		int buttonH = lineH + 9;
		int buttonY = contentTop + contentH + 6;
		int width = Math.max(textX + text.stringWidth(name),
				valueX + Math.max(text.stringWidth(buyValue), text.stringWidth(sellValue))) + PAD;
		int height = buttonY + buttonH + PAD;
		BackgroundComponent background = new BackgroundComponent();
		background.setRectangle(new Rectangle(0, 0, width, height));
		background.render(graphics);
		if (logo != null) {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(logo, PAD, PAD + (headerH - LOGO_SIZE) / 2, LOGO_SIZE, LOGO_SIZE, null);
		}
		text(graphics, "GE Uncut", PAD + LOGO_SIZE + 5, PAD + (headerH - lineH) / 2 + ascent, LOGO);
		if (icon != null) { graphics.drawImage(icon, PAD, contentTop + (contentH - iconH) / 2, null); }
		int nameBaseline = contentTop + (contentH - textBlockH) / 2 + ascent;
		text(graphics, name, textX, nameBaseline, NAME);
		text(graphics, "Buy", textX, nameBaseline + lineH, LABEL);
		text(graphics, "Sell", textX, nameBaseline + lineH * 2, LABEL);
		value(graphics, buyValue, valueX, nameBaseline + lineH);
		value(graphics, sellValue, valueX, nameBaseline + lineH * 2);
		Rectangle button = new Rectangle(PAD, buttonY, width - 2 * PAD, buttonH);
		graphics.setColor(detailsHover ? BUTTON_FILL_HOVER : BUTTON_FILL);
		graphics.fillRoundRect(button.x, button.y, button.width, button.height, 7, 7);
		graphics.setColor(detailsHover ? LOGO : BUTTON_BORDER);
		graphics.drawRoundRect(button.x, button.y, button.width, button.height, 7, 7);
		String label = "Item details";
		text(graphics, label, button.x + (button.width - text.stringWidth(label)) / 2,
				button.y + (button.height - lineH) / 2 + ascent, detailsHover ? VALUE : LOGO);
		detailsBounds = button;
		return new Dimension(width, height);
	}

	private static String quote(Long price, String timestamp, Instant now) {
		return price == null ? "-" : String.format(Locale.ENGLISH, "%,d", price) + "   " + PriceHint.ago(timestamp, now);
	}

	private static String fit(String value, FontMetrics metrics, int width) {
		if (metrics.stringWidth(value) <= width) { return value; }
		int end = value.length();
		while (end > 0 && metrics.stringWidth(value.substring(0, end) + "...") > width) { end--; }
		return value.substring(0, end) + "...";
	}

	private void text(Graphics2D graphics, String content, int x, int baseline, Color color) {
		TextComponent component = new TextComponent();
		component.setText(content);
		component.setColor(color);
		component.setPosition(new Point(x, baseline));
		component.render(graphics);
	}

	private void value(Graphics2D graphics, String content, int x, int baseline) {
		int split = content.indexOf("   ");
		if (split < 0) {
			text(graphics, content, x, baseline, VALUE);
			return;
		}
		String amount = content.substring(0, split);
		String ago = content.substring(split);
		text(graphics, amount, x, baseline, VALUE);
		text(graphics, ago, x + graphics.getFontMetrics().stringWidth(amount), baseline, AGO);
	}
}
