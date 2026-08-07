package app.geuncut.ui;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Clicking an open picker has to shut it.
 *
 * Swing's popup grabber dismisses the menu on the press BEFORE the press reaches the
 * component, so the old handler saw a closed menu and reopened it: the picker looked
 * stuck open. The fix keys off how long ago the menu was hidden, and this covers that
 * decision. Driving real popups is not tested here on purpose: showing a JFrame in the
 * Gradle test JVM never exits, and the resulting hang tells you nothing about the bug.
 */
public class FlatSelectToggleTest {
	private FlatSelect select;

	@Before
	public void setUp() {
		Theme.apply(Theme.Mode.DARK);
		select = new FlatSelect(new String[] { "One", "Two", "Three" }, 0);
	}

	@Test
	public void opensWhenNothingWasShowing() {
		assertTrue(select.shouldOpen(1_000_000L));
	}

	@Test
	public void aPressRightAfterTheDismissalShutsItInstead() {
		// The grabber hid the menu, then the same physical click arrives a moment later.
		select.markHidden(1_000_000L);
		assertFalse("clicking an open picker reopened it instead of shutting it",
				select.shouldOpen(1_000_001L));
	}

	@Test
	public void theSuppressionIsSpentAfterOneUse() {
		// Otherwise the picker would refuse to open a second time.
		select.markHidden(1_000_000L);
		assertFalse(select.shouldOpen(1_000_001L));
		assertTrue("the picker stayed wedged shut after one toggle",
				select.shouldOpen(1_000_002L));
	}

	@Test
	public void adeliberateSecondClickStillOpensIt() {
		// Choosing an item also hides the menu. Going back in must work.
		select.markHidden(1_000_000L);
		assertTrue("a later click was swallowed", select.shouldOpen(1_000_400L));
	}

	@Test
	public void theWindowIsShorterThanADoubleClick() {
		// A fast double-click is ~120ms apart; the window must sit well below that or
		// the second click of an impatient user gets eaten.
		select.markHidden(1_000_000L);
		assertTrue(select.shouldOpen(1_000_000L + 120));
	}
}
