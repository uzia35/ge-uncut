package app.geuncut.tracker;

import java.time.Instant;
import java.util.Optional;

import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Test;

import static net.runelite.api.GrandExchangeOfferState.BOUGHT;
import static net.runelite.api.GrandExchangeOfferState.BUYING;
import static net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY;
import static net.runelite.api.GrandExchangeOfferState.CANCELLED_SELL;
import static net.runelite.api.GrandExchangeOfferState.EMPTY;
import static net.runelite.api.GrandExchangeOfferState.SELLING;
import static net.runelite.api.GrandExchangeOfferState.SOLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferTrackerTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");
	private static final int TBOW = 20997;

	private OfferTracker tracker;

	@Before
	public void setUp() {
		tracker = new OfferTrackerImpl();
	}

	private Optional<OfferDelta> change(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int spent, int totalQuantity, int price) {
		return tracker.onOfferChanged(slot, itemId, state, quantitySold, spent, totalQuantity, price, T0);
	}

	@Test
	public void freshBuyEmitsDeltaPerFill() {
		assertFalse(change(0, TBOW, BUYING, 0, 0, 8, 1_000_000).isPresent());

		Optional<OfferDelta> first = change(0, TBOW, BUYING, 3, 3_000_000, 8, 1_000_000);
		assertTrue(first.isPresent());
		assertEquals(OfferDelta.Side.BUY, first.get().getSide());
		assertEquals(3, first.get().getQuantity());
		assertEquals(1_000_000, first.get().getPriceEach());

		Optional<OfferDelta> second = change(0, TBOW, BOUGHT, 8, 8_100_000, 8, 1_000_000);
		assertTrue(second.isPresent());
		assertEquals(5, second.get().getQuantity());
		assertEquals(1_020_000, second.get().getPriceEach());
	}

	@Test
	public void loginReplayOfPartialOfferIsBaselineNotFill() {
		Optional<OfferDelta> replay = change(2, TBOW, BUYING, 5, 5_000_000, 8, 1_000_000);
		assertFalse(replay.isPresent());

		Optional<OfferDelta> next = change(2, TBOW, BUYING, 6, 6_000_000, 8, 1_000_000);
		assertTrue(next.isPresent());
		assertEquals(1, next.get().getQuantity());
	}

	@Test
	public void relogWithSameOfferDoesNotDoubleCount() {
		change(1, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(1, TBOW, BUYING, 4, 4_000_000, 8, 1_000_000);
		tracker.reset();

		Optional<OfferDelta> replay = change(1, TBOW, BUYING, 4, 4_000_000, 8, 1_000_000);
		assertFalse(replay.isPresent());
	}

	@Test
	public void cancelledBuyEmitsOnlyTheFilledPortion() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(0, TBOW, BUYING, 2, 2_000_000, 8, 1_000_000);

		Optional<OfferDelta> onCancel = change(0, TBOW, CANCELLED_BUY, 2, 2_000_000, 8, 1_000_000);
		assertFalse(onCancel.isPresent());
	}

	@Test
	public void sellSideEmitsSellDeltas() {
		change(3, TBOW, SELLING, 0, 0, 8, 1_500_000);
		Optional<OfferDelta> sold = change(3, TBOW, SOLD, 8, 12_000_000, 8, 1_500_000);
		assertTrue(sold.isPresent());
		assertEquals(OfferDelta.Side.SELL, sold.get().getSide());
		assertEquals(8, sold.get().getQuantity());
		assertEquals(1_500_000, sold.get().getPriceEach());
	}

	@Test
	public void emptySlotResetsAndNextOfferStartsClean() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);
		change(0, TBOW, EMPTY, 0, 0, 0, 0);

		assertFalse(change(0, TBOW, BUYING, 0, 0, 8, 1_000_000).isPresent());
		Optional<OfferDelta> fill = change(0, TBOW, BUYING, 1, 1_000_000, 8, 1_000_000);
		assertTrue(fill.isPresent());
		assertEquals(1, fill.get().getQuantity());
	}

	@Test
	public void reusedSlotWhoseFirstEventAlreadyFilledIsCounted() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);
		change(0, TBOW, EMPTY, 0, 0, 0, 0);

		Optional<OfferDelta> fill = change(0, 4151, BUYING, 5, 5_000_000, 8, 1_000_000);
		assertTrue(fill.isPresent());
		assertEquals(5, fill.get().getQuantity());
		assertEquals(1_000_000, fill.get().getPriceEach());
	}

	@Test
	public void largeSpendComputesAnExactUnitPrice() {
		change(1, TBOW, BUYING, 0, 0, 1, 1_500_000_001);
		Optional<OfferDelta> fill = change(1, TBOW, BOUGHT, 1, 1_500_000_001, 1, 1_500_000_001);
		assertTrue(fill.isPresent());
		assertEquals(1_500_000_001, fill.get().getPriceEach());
	}

	@Test
	public void slotReuseWithDifferentItemStartsClean() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);

		Optional<OfferDelta> otherItem = change(0, 4151, BUYING, 2, 3_000_000, 8, 1_500_000);
		assertTrue(otherItem.isPresent());
		assertEquals(2, otherItem.get().getQuantity());
		assertEquals(1_500_000, otherItem.get().getPriceEach());
	}

	// --- Residual fills: a slot that empties short of its total -----------------

	@Test
	public void completionRacingCollectionBooksTheResidualAtTheOfferPrice() {
		// The 2026-07-19 bones case: login replays the sell at 3,430/4,000, the
		// last 570 fill and collection collapse into EMPTY with no SOLD event.
		change(2, TBOW, SELLING, 0, 0, 4_000, 21_000);
		Optional<OfferDelta> catchUp = change(2, TBOW, SELLING, 3_430, 72_030_000, 4_000, 21_000);
		assertTrue(catchUp.isPresent());
		assertEquals(3_430, catchUp.get().getQuantity());

		Optional<OfferDelta> residual = change(2, TBOW, EMPTY, 0, 0, 0, 0);
		assertTrue(residual.isPresent());
		assertEquals(OfferDelta.Side.SELL, residual.get().getSide());
		assertEquals(570, residual.get().getQuantity());
		assertEquals(21_000, residual.get().getPriceEach());
	}

	@Test
	public void buySideCompletionRaceAlsoBooksTheResidual() {
		change(1, TBOW, BUYING, 0, 0, 10, 500);
		change(1, TBOW, BUYING, 4, 2_000, 10, 500);

		Optional<OfferDelta> residual = change(1, TBOW, EMPTY, 0, 0, 0, 0);
		assertTrue(residual.isPresent());
		assertEquals(OfferDelta.Side.BUY, residual.get().getSide());
		assertEquals(6, residual.get().getQuantity());
		assertEquals(500, residual.get().getPriceEach());
	}

	@Test
	public void abortedOfferClearsWithNoResidual() {
		// An abort always shows its CANCELLED state before the collect empties
		// the slot; the unfilled remainder was never traded.
		change(0, TBOW, SELLING, 0, 0, 4_000, 21_000);
		change(0, TBOW, SELLING, 3_430, 72_030_000, 4_000, 21_000);
		change(0, TBOW, CANCELLED_SELL, 3_430, 72_030_000, 4_000, 21_000);

		assertFalse(change(0, TBOW, EMPTY, 0, 0, 0, 0).isPresent());
	}

	@Test
	public void completedOfferCollectedNormallyHasNoResidual() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);

		assertFalse(change(0, TBOW, EMPTY, 0, 0, 0, 0).isPresent());
	}

	@Test
	public void slotCollectedWhileAwayHasNoSnapshotAndNoResidual() {
		// Collected from another client while this one was off: the login replay
		// is EMPTY with nothing known, so nothing may be invented.
		assertFalse(change(3, TBOW, EMPTY, 0, 0, 0, 0).isPresent());
	}
}
