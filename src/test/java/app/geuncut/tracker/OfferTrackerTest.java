package app.geuncut.tracker;

import java.time.Instant;
import java.util.Optional;

import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import org.junit.Before;
import org.junit.Test;

import static net.runelite.api.GrandExchangeOfferState.BOUGHT;
import static net.runelite.api.GrandExchangeOfferState.BUYING;
import static net.runelite.api.GrandExchangeOfferState.CANCELLED_BUY;
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

	@Test
	public void freshBuyEmitsDeltaPerFill() {
		assertFalse(tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0).isPresent());

		Optional<OfferDelta> first = tracker.onOfferChanged(0, TBOW, BUYING, 3, 3_000_000, T0);
		assertTrue(first.isPresent());
		assertEquals(OfferDelta.Side.BUY, first.get().getSide());
		assertEquals(3, first.get().getQuantity());
		assertEquals(1_000_000, first.get().getPriceEach());

		Optional<OfferDelta> second = tracker.onOfferChanged(0, TBOW, BOUGHT, 8, 8_100_000, T0);
		assertTrue(second.isPresent());
		assertEquals(5, second.get().getQuantity());
		assertEquals(1_020_000, second.get().getPriceEach());
	}

	@Test
	public void loginReplayOfPartialOfferIsBaselineNotFill() {
		Optional<OfferDelta> replay = tracker.onOfferChanged(2, TBOW, BUYING, 5, 5_000_000, T0);
		assertFalse(replay.isPresent());

		Optional<OfferDelta> next = tracker.onOfferChanged(2, TBOW, BUYING, 6, 6_000_000, T0);
		assertTrue(next.isPresent());
		assertEquals(1, next.get().getQuantity());
	}

	@Test
	public void relogWithSameOfferDoesNotDoubleCount() {
		tracker.onOfferChanged(1, TBOW, BUYING, 0, 0, T0);
		tracker.onOfferChanged(1, TBOW, BUYING, 4, 4_000_000, T0);
		tracker.reset();

		Optional<OfferDelta> replay = tracker.onOfferChanged(1, TBOW, BUYING, 4, 4_000_000, T0);
		assertFalse(replay.isPresent());
	}

	@Test
	public void cancelledBuyEmitsOnlyTheFilledPortion() {
		tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0);
		tracker.onOfferChanged(0, TBOW, BUYING, 2, 2_000_000, T0);

		Optional<OfferDelta> onCancel = tracker.onOfferChanged(0, TBOW, CANCELLED_BUY, 2, 2_000_000, T0);
		assertFalse(onCancel.isPresent());
	}

	@Test
	public void sellSideEmitsSellDeltas() {
		tracker.onOfferChanged(3, TBOW, SELLING, 0, 0, T0);
		Optional<OfferDelta> sold = tracker.onOfferChanged(3, TBOW, SOLD, 8, 12_000_000, T0);
		assertTrue(sold.isPresent());
		assertEquals(OfferDelta.Side.SELL, sold.get().getSide());
		assertEquals(8, sold.get().getQuantity());
		assertEquals(1_500_000, sold.get().getPriceEach());
	}

	@Test
	public void emptySlotResetsAndNextOfferStartsClean() {
		tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0);
		tracker.onOfferChanged(0, TBOW, BOUGHT, 8, 8_000_000, T0);
		tracker.onOfferChanged(0, TBOW, EMPTY, 0, 0, T0);

		assertFalse(tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0).isPresent());
		Optional<OfferDelta> fill = tracker.onOfferChanged(0, TBOW, BUYING, 1, 1_000_000, T0);
		assertTrue(fill.isPresent());
		assertEquals(1, fill.get().getQuantity());
	}

	@Test
	public void reusedSlotWhoseFirstEventAlreadyFilledIsCounted() {
		tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0);
		tracker.onOfferChanged(0, TBOW, BOUGHT, 8, 8_000_000, T0);
		tracker.onOfferChanged(0, TBOW, EMPTY, 0, 0, T0);

		Optional<OfferDelta> fill = tracker.onOfferChanged(0, 4151, BUYING, 5, 5_000_000, T0);
		assertTrue(fill.isPresent());
		assertEquals(5, fill.get().getQuantity());
		assertEquals(1_000_000, fill.get().getPriceEach());
	}

	@Test
	public void largeSpendComputesAnExactUnitPrice() {
		tracker.onOfferChanged(1, TBOW, BUYING, 0, 0, T0);
		Optional<OfferDelta> fill = tracker.onOfferChanged(1, TBOW, BOUGHT, 1, 1_500_000_001, T0);
		assertTrue(fill.isPresent());
		assertEquals(1_500_000_001, fill.get().getPriceEach());
	}

	@Test
	public void slotReuseWithDifferentItemStartsClean() {
		tracker.onOfferChanged(0, TBOW, BUYING, 0, 0, T0);
		tracker.onOfferChanged(0, TBOW, BOUGHT, 8, 8_000_000, T0);

		Optional<OfferDelta> otherItem = tracker.onOfferChanged(0, 4151, BUYING, 2, 3_000_000, T0);
		assertTrue(otherItem.isPresent());
		assertEquals(2, otherItem.get().getQuantity());
		assertEquals(1_500_000, otherItem.get().getPriceEach());
	}
}
