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

import app.geuncut.dto.ItemPrice;
import app.geuncut.tracker.PriceHint;
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

	public OfferPriceOverlay() {
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public void show(ItemPrice price, String itemName, BufferedImage icon) {
		this.price = price;
		this.itemName = itemName;
		this.icon = icon;
	}

	public void clear() {
		this.price = null;
		this.itemName = null;
		this.icon = null;
		this.detailsBounds = null;
		this.detailsHover = false;
	}

	public boolean isDetailsClick(Point canvasPoint) {
		Rectangle local = detailsBounds;
		Rectangle bounds = getBounds();
		if (local == null || bounds == null || price == null) {
			return false;
		}
		return canvasPoint.x >= bounds.x + local.x && canvasPoint.x <= bounds.x + local.x + local.width
				&& canvasPoint.y >= bounds.y + local.y && canvasPoint.y <= bounds.y + local.y + local.height;
	}

	public void setDetailsHover(boolean hover) {
		this.detailsHover = hover;
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		ItemPrice current = price;
		if (current == null || !PriceHint.hasPrices(current)) {
			return null;
		}
		Instant now = Instant.now();
		String name = itemName != null && !itemName.isEmpty() ? itemName : "GE Uncut";
		String buyValue = String.format("%,d", current.getBuyPrice()) + "   " + PriceHint.ago(current.getLowTime(), now);
		String sellValue = String.format("%,d", current.getSellPrice()) + "   " + PriceHint.ago(current.getHighTime(), now);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics text = graphics.getFontMetrics();
		int lineH = text.getHeight();
		int ascent = text.getAscent();
		int labelCol = text.stringWidth("Sell") + 10;

		int iconW = icon != null ? icon.getWidth() : 36;
		int iconH = icon != null ? icon.getHeight() : 32;

		int headerH = Math.max(LOGO_SIZE, lineH);
		int contentTop = PAD + headerH + 6;
		int textX = PAD + iconW + 12;
		int valueX = textX + labelCol;
		int textBlockH = lineH * 3;
		int contentH = Math.max(iconH, textBlockH);
		int buttonGap = 6;
		int buttonH = lineH + 9;
		int height = contentTop + contentH + buttonGap + buttonH + PAD;

		int rightEdge = Math.max(textX + text.stringWidth(name),
				valueX + Math.max(text.stringWidth(buyValue), text.stringWidth(sellValue)));
		rightEdge = Math.max(rightEdge, PAD + LOGO_SIZE + 5 + text.stringWidth("GE Uncut"));
		rightEdge = Math.max(rightEdge, PAD + text.stringWidth("Item details") + 24);
		int width = rightEdge + PAD;

		BackgroundComponent background = new BackgroundComponent();
		background.setRectangle(new Rectangle(0, 0, width, height));
		background.render(graphics);

		if (logo != null) {
			Graphics2D logoGraphics = (Graphics2D) graphics.create();
			logoGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			logoGraphics.drawImage(logo, PAD, PAD + (headerH - LOGO_SIZE) / 2, LOGO_SIZE, LOGO_SIZE, null);
			logoGraphics.dispose();
		}
		text(graphics, "GE Uncut", PAD + LOGO_SIZE + 5, PAD + (headerH - lineH) / 2 + ascent, LOGO);

		if (icon != null) {
			int iconY = contentTop + (contentH - iconH) / 2;
			graphics.drawImage(icon, PAD, iconY, null);
		}

		int textTop = contentTop + (contentH - textBlockH) / 2;
		int nameBaseline = textTop + ascent;
		int buyBaseline = nameBaseline + lineH;
		int sellBaseline = buyBaseline + lineH;

		text(graphics, name, textX, nameBaseline, NAME);
		text(graphics, "Buy", textX, buyBaseline, LABEL);
		text(graphics, "Sell", textX, sellBaseline, LABEL);
		value(graphics, buyValue, valueX, buyBaseline);
		value(graphics, sellValue, valueX, sellBaseline);

		int buttonY = contentTop + contentH + buttonGap;
		Rectangle button = new Rectangle(PAD, buttonY, width - 2 * PAD, buttonH);
		graphics.setColor(detailsHover ? BUTTON_FILL_HOVER : BUTTON_FILL);
		graphics.fillRoundRect(button.x, button.y, button.width, button.height, 7, 7);
		graphics.setColor(detailsHover ? LOGO : BUTTON_BORDER);
		graphics.drawRoundRect(button.x, button.y, button.width, button.height, 7, 7);
		String label = "Item details";
		int labelX = button.x + (button.width - text.stringWidth(label)) / 2;
		int labelBaseline = button.y + (button.height - text.getHeight()) / 2 + ascent;
		graphics.setColor(Color.BLACK);
		graphics.drawString(label, labelX + 1, labelBaseline + 1);
		graphics.setColor(detailsHover ? VALUE : LOGO);
		graphics.drawString(label, labelX, labelBaseline);
		detailsBounds = button;

		return new Dimension(width, height);
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
