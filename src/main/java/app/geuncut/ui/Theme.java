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

	/** Panel colour mode. Dark is RuneLite's native look; light mirrors the website. */
	enum Mode {
		DARK, LIGHT
	}

	static Color SURFACE;
	static Color RAISED;
	static Color HOVER;
	static Color TRACK;
	static Color LINE;
	static Color LINE_STRONG;
	static Color INK;
	static Color WHITE;
	static Color MUTED;
	static Color FAINT;
	static Color UP;
	static Color DOWN;
	static Color AMBER;
	static Color INFO;
	static Color PILL_INK;

	private static Mode mode = Mode.DARK;

	static {
		apply(Mode.DARK);
	}

	static Mode mode() {
		return mode;
	}

	/** Repaint the palette for a mode. Call before rebuilding the panel. */
	static void apply(Mode target) {
		mode = target;
		if (target == Mode.LIGHT) {
			SURFACE = new Color(0xF4, 0xF4, 0xF3);
			RAISED = new Color(0xFF, 0xFF, 0xFF);
			HOVER = new Color(0xEC, 0xEC, 0xEA);
			TRACK = new Color(0xE7, 0xE7, 0xE4);
			LINE = new Color(0xE4, 0xE4, 0xE1);
			LINE_STRONG = new Color(0xD2, 0xD2, 0xCE);
			INK = new Color(0x14, 0x15, 0x1A);
			WHITE = new Color(0x0A, 0x0B, 0x0F);
			MUTED = new Color(0x49, 0x4C, 0x57);
			FAINT = new Color(0x6B, 0x6E, 0x7A);
			UP = new Color(0x15, 0x80, 0x3D);
			DOWN = new Color(0xC4, 0x1F, 0x1F);
			AMBER = new Color(0xB4, 0x53, 0x09);
			INFO = new Color(0x25, 0x63, 0xEB);
			PILL_INK = new Color(0xFF, 0xFF, 0xFF);
		} else {
			SURFACE = new Color(0x14, 0x14, 0x14);
			RAISED = new Color(0x1E, 0x1E, 0x1E);
			HOVER = new Color(0x2A, 0x2A, 0x2A);
			TRACK = new Color(0x14, 0x14, 0x14);
			LINE = new Color(0x2E, 0x2E, 0x2E);
			LINE_STRONG = new Color(0x3E, 0x3E, 0x3E);
			INK = new Color(0xED, 0xED, 0xF0);
			WHITE = new Color(0xFF, 0xFF, 0xFF);
			MUTED = new Color(0xC2, 0xC2, 0xCA);
			FAINT = new Color(0x9A, 0x9A, 0xA4);
			UP = new Color(0x2E, 0xC2, 0x7E);
			DOWN = new Color(0xF4, 0x57, 0x4D);
			AMBER = new Color(0xE8, 0xA1, 0x3C);
			INFO = new Color(0x4D, 0x9F, 0xFF);
			PILL_INK = new Color(0x10, 0x13, 0x0F);
		}
	}

	static Color soft(Color base) {
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), 34);
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
	static final Font SMALL_BOLD = INTER_SEMI.deriveFont(11f);
	static final Font PILL = INTER_SEMI.deriveFont(9.5f);
	static final Font SECTION = INTER_SEMI.deriveFont(10.5f);
	static final Font NUM = MONO.deriveFont(12f);
	static final Font NUM_BOLD = MONO_SEMI.deriveFont(12f);
	static final Font NUM_LG = MONO_SEMI.deriveFont(14f);
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
