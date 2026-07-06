package app.geuncut.ui;

import java.awt.Color;
import java.awt.Font;

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

	static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
	static final Font SECTION = new Font(Font.SANS_SERIF, Font.BOLD, 10);
	static final Font NUM = new Font(Font.MONOSPACED, Font.PLAIN, 12);
	static final Font NUM_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);
	static final Font NUM_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 10);
}
