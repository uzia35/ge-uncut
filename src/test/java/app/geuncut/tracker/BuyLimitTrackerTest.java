package app.geuncut.tracker;

import java.time.Duration;
import java.time.Instant;

import app.geuncut.tracker.impl.BuyLimitTrackerImpl;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuyLimitTrackerTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");
	private static final int TBOW = 20997;

	private BuyLimitTracker tracker;

	@Before
	public void setUp() {
		tracker = new BuyLimitTrackerImpl();
	}

	@Test
	public void sumsPurchasesInsideTheWindow() {
		tracker.recordBuy(TBOW, 3, T0);
		tracker.recordBuy(TBOW, 2, T0.plus(Duration.ofMinutes(30)));
		assertEquals(5, tracker.boughtInWindow(TBOW, T0.plus(Duration.ofHours(1))));
	}

	@Test
	public void purchasesRollOffAfterFourHours() {
		tracker.recordBuy(TBOW, 3, T0);
		tracker.recordBuy(TBOW, 2, T0.plus(Duration.ofHours(2)));

		Instant later = T0.plus(Duration.ofHours(4)).plusSeconds(1);
		assertEquals(2, tracker.boughtInWindow(TBOW, later));
	}

	@Test
	public void nextResetIsOldestPurchasePlusWindow() {
		tracker.recordBuy(TBOW, 3, T0);
		tracker.recordBuy(TBOW, 2, T0.plus(Duration.ofHours(1)));

		assertEquals(T0.plus(Duration.ofHours(4)),
				tracker.nextReset(TBOW, T0.plus(Duration.ofHours(2))).orElseThrow(AssertionError::new));
	}

	@Test
	public void noPurchasesMeansNoReset() {
		assertFalse(tracker.nextReset(TBOW, T0).isPresent());
		assertEquals(0, tracker.boughtInWindow(TBOW, T0));
	}

	@Test
	public void itemsAreIndependent() {
		tracker.recordBuy(TBOW, 8, T0);
		assertEquals(0, tracker.boughtInWindow(4151, T0.plusSeconds(60)));
		assertTrue(tracker.nextReset(TBOW, T0.plusSeconds(60)).isPresent());
	}
}
