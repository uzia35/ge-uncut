package app.geuncut.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * GE Uncut brand palette and fonts for the panel, mirroring the website's
 * monochrome trading-desk theme: near-black surfaces, a near-white accent,
 * monospace numerals, and semantic colour only where it means something.
 */
final class Theme {
	private Theme() {
	}

	static final Color SURFACE = new Color(0x14, 0x14, 0x16);
	static final Color RAISED = new Color(0x1B, 0x1B, 0x1F);
	static final Color HOVER = new Color(0x24, 0x24, 0x2A);
	static final Color TRACK = new Color(0x0E, 0x0E, 0x10);
	static final Color LINE = new Color(0x26, 0x26, 0x2A);
	static final Color LINE_STRONG = new Color(0x36, 0x36, 0x3C);

	static final Color INK = new Color(0xED, 0xED, 0xF0);
	static final Color WHITE = new Color(0xFF, 0xFF, 0xFF);
	static final Color MUTED = new Color(0x9A, 0x9A, 0xA4);
	static final Color FAINT = new Color(0x6C, 0x6C, 0x76);

	static final Color UP = new Color(0x2E, 0xC2, 0x7E);
	static final Color DOWN = new Color(0xF4, 0x57, 0x4D);
	static final Color AMBER = new Color(0xE8, 0xA1, 0x3C);
	static final Color INFO = new Color(0x4D, 0x9F, 0xFF);

	// Translucent fills for pills and alert grounds (alpha over the dark surface).
	static Color soft(Color base) {
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), 34);
	}

	static Color line(Color base) {
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), 90);
	}

	// The website's faces, bundled so the panel matches it exactly: Inter for
	// text, JetBrains Mono for numerals. Falls back to logical fonts if a
	// resource is somehow missing.
	private static final Font INTER = load("/fonts/Inter-Regular.ttf", Font.SANS_SERIF);
	private static final Font INTER_SEMI = load("/fonts/Inter-SemiBold.ttf", Font.SANS_SERIF);
	private static final Font MONO = load("/fonts/JetBrainsMono-Regular.ttf", Font.MONOSPACED);
	private static final Font MONO_SEMI = load("/fonts/JetBrainsMono-Bold.ttf", Font.MONOSPACED);

	static final Font BODY = INTER.deriveFont(12.5f);
	static final Font BODY_BOLD = INTER_SEMI.deriveFont(12.5f);
	static final Font SMALL = INTER.deriveFont(11f);
	static final Font SECTION = INTER_SEMI.deriveFont(10.5f);
	static final Font NUM = MONO.deriveFont(12f);
	static final Font NUM_BOLD = MONO_SEMI.deriveFont(12f);
	static final Font NUM_SMALL = MONO.deriveFont(11f);
	static final Font NUM_TINY = MONO.deriveFont(9.5f);
	static final Font NUM_HERO = MONO_SEMI.deriveFont(20f);

	private static Font load(String resource, String fallbackFamily) {
		try (InputStream in = Theme.class.getResourceAsStream(resource)) {
			if (in != null) {
				return Font.createFont(Font.TRUETYPE_FONT, in);
			}
		} catch (FontFormatException | IOException unreadable) {
			// Fall through to the logical-font fallback.
		}
		return new Font(fallbackFamily, Font.PLAIN, 12);
	}
}
