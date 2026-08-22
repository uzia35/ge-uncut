package app.geuncut.tracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import app.geuncut.model.OfferDelta;
import app.geuncut.model.OfferSnapshot;
import app.geuncut.tracker.impl.InMemorySnapshotStore;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OfferTrackerTest {
	private static final Instant T0 = Instant.parse("2026-07-05T12:00:00Z");
	private static final int TBOW = 20997;

	private OfferTracker tracker;

	@Before
	public void setUp() {
		tracker = new OfferTrackerImpl();
	}

	private static Optional<OfferDelta> change(OfferTracker target, int slot, int itemId,
			GrandExchangeOfferState state, int quantitySold, int spent, int totalQuantity, int price) {
		List<OfferDelta> fills = new ArrayList<>();
		target.onOfferChanged(slot, itemId, state, quantitySold, spent, totalQuantity, price, T0, fills::add);
		return fills.isEmpty() ? Optional.empty() : Optional.of(fills.get(0));
	}

	private Optional<OfferDelta> change(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int spent, int totalQuantity, int price) {
		return change(tracker, slot, itemId, state, quantitySold, spent, totalQuantity, price);
	}

	@Test
	public void freshBuyEmitsDeltaPerFill() {
		assertFalse(change(0, TBOW, BUYING, 0, 0, 8, 1_000_000).isPresent());

		Optional<OfferDelta> first = change(0, TBOW, BUYING, 3, 3_000_000, 8, 1_000_000);
		assertTrue(first.isPresent());
		assertEquals(OfferDelta.Side.BUY, first.get().getSide());
		assertEquals(3, first.get().getQuantity());
		assertEquals(1_000_000, first.get().getPriceEach());
		assertEquals(3, first.get().getCumulativeQuantitySold());

		Optional<OfferDelta> second = change(0, TBOW, BOUGHT, 8, 8_100_000, 8, 1_000_000);
		assertTrue(second.isPresent());
		assertEquals(5, second.get().getQuantity());
		assertEquals(1_020_000, second.get().getPriceEach());
		assertEquals(8, second.get().getCumulativeQuantitySold());
	}

	@Test
	public void oneOfferKeepsOneIdAcrossItsFills() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		Optional<OfferDelta> first = change(0, TBOW, BUYING, 3, 3_000_000, 8, 1_000_000);
		Optional<OfferDelta> second = change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);

		assertNotNull(first.get().getOfferId());
		assertEquals(first.get().getOfferId(), second.get().getOfferId());
	}

	@Test
	public void aNewOfferInTheSameSlotGetsANewId() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		Optional<OfferDelta> first = change(0, TBOW, BOUGHT, 8, 8_000_000, 8, 1_000_000);
		change(0, TBOW, EMPTY, 0, 0, 0, 0);

		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		Optional<OfferDelta> second = change(0, TBOW, BUYING, 8, 8_000_000, 8, 1_000_000);

		assertTrue(second.isPresent());
		assertFalse(first.get().getOfferId().equals(second.get().getOfferId()));
	}

	@Test
	public void aDifferentItemInTheSameSlotGetsANewId() {
		change(0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		Optional<OfferDelta> first = change(0, TBOW, BUYING, 2, 2_000_000, 8, 1_000_000);
		Optional<OfferDelta> other = change(0, 4151, BUYING, 2, 3_000_000, 8, 1_500_000);

		assertTrue(other.isPresent());
		assertFalse(first.get().getOfferId().equals(other.get().getOfferId()));
	}

	@Test
	public void aQuantityThatGoesBackwardsStartsANewOffer() {
		change(2, TBOW, BUYING, 0, 0, 8, 1_000_000);
		Optional<OfferDelta> first = change(2, TBOW, BUYING, 5, 5_000_000, 8, 1_000_000);
		change(2, TBOW, BUYING, 1, 1_000_000, 8, 1_000_000);
		Optional<OfferDelta> after = change(2, TBOW, BUYING, 4, 4_000_000, 8, 1_000_000);

		assertTrue(after.isPresent());
		assertFalse(first.get().getOfferId().equals(after.get().getOfferId()));
	}

	@Test
	public void theOfferIdIsPersistedWithTheBaseline() {
		InMemorySnapshotStore store = new InMemorySnapshotStore();
		OfferTracker durable = new OfferTrackerImpl(store);
		durable.loadFor("acct-1");
		change(durable, 3, TBOW, SELLING, 0, 0, 6_250, 20_400);
		Optional<OfferDelta> live = change(durable, 3, TBOW, SELLING, 2_175, 44_370_000, 6_250, 20_400);

		OfferSnapshot stored = store.load("acct-1").get(3);
		assertEquals(live.get().getOfferId(), stored.getOfferId());

		durable.reset();
		durable.loadFor("acct-1");
		Optional<OfferDelta> awayFill = change(durable, 3, TBOW, SOLD, 6_250, 127_500_000, 6_250, 20_400);
		assertEquals("the same offer must keep its id across a relog",
				live.get().getOfferId(), awayFill.get().getOfferId());
		assertEquals(6_250, awayFill.get().getCumulativeQuantitySold());
	}

	@Test
	public void aBaselineFromBeforeOfferIdsIsGivenOneOnFirstTouch() {
		InMemorySnapshotStore store = new InMemorySnapshotStore();
		Map<Integer, OfferSnapshot> legacy = new HashMap<>();
		legacy.put(1, new OfferSnapshot(TBOW, 100, 100_000_000, SELLING, 500, 1_000_000, null));
		store.save("acct-1", legacy);

		OfferTracker durable = new OfferTrackerImpl(store);
		durable.loadFor("acct-1");
		Optional<OfferDelta> fill = change(durable, 1, TBOW, SELLING, 150, 150_000_000, 500, 1_000_000);

		assertTrue(fill.isPresent());
		assertNotNull(fill.get().getOfferId());
		assertEquals(fill.get().getOfferId(), store.load("acct-1").get(1).getOfferId());
	}

	@Test
	public void theFillIsHandedOverBeforeTheBaselineIsPersisted() {
		List<String> order = new ArrayList<>();
		SnapshotStore recording = new SnapshotStore() {
			@Override
			public Map<Integer, OfferSnapshot> load(String accountHash) {
				return new HashMap<>();
			}

			@Override
			public void save(String accountHash, Map<Integer, OfferSnapshot> slots) {
				order.add("persist");
			}
		};
		OfferTracker durable = new OfferTrackerImpl(recording);
		durable.loadFor("acct-1");
		change(durable, 0, TBOW, BUYING, 0, 0, 8, 1_000_000);
		order.clear();

		durable.onOfferChanged(0, TBOW, BUYING, 3, 3_000_000, 8, 1_000_000, T0, delta -> order.add("append"));

		assertEquals(Arrays.asList("append", "persist"), order);
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


	@Test
	public void completionRacingCollectionBooksTheResidualAtTheOfferPrice() {
		change(2, TBOW, SELLING, 0, 0, 4_000, 21_000);
		Optional<OfferDelta> catchUp = change(2, TBOW, SELLING, 3_430, 72_030_000, 4_000, 21_000);
		assertTrue(catchUp.isPresent());
		assertEquals(3_430, catchUp.get().getQuantity());

		Optional<OfferDelta> residual = change(2, TBOW, EMPTY, 0, 0, 0, 0);
		assertTrue(residual.isPresent());
		assertEquals(OfferDelta.Side.SELL, residual.get().getSide());
		assertEquals(570, residual.get().getQuantity());
		assertEquals(21_000, residual.get().getPriceEach());
		assertEquals(catchUp.get().getOfferId(), residual.get().getOfferId());
		assertEquals(4_000, residual.get().getCumulativeQuantitySold());
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
		assertFalse(change(3, TBOW, EMPTY, 0, 0, 0, 0).isPresent());
	}

	@Test
	public void reloadedBaselineAcrossRelogEmitsTheAwayFills() {
		InMemorySnapshotStore store = new InMemorySnapshotStore();
		OfferTracker durable = new OfferTrackerImpl(store);
		durable.loadFor("acct-1");

		assertFalse(change(durable, 3, TBOW, SELLING, 0, 0, 6_250, 20_400).isPresent());
		Optional<OfferDelta> live = change(durable, 3, TBOW, SELLING, 2_175, 44_370_000, 6_250, 20_400);
		assertTrue(live.isPresent());
		assertEquals(2_175, live.get().getQuantity());

		durable.reset();
		durable.loadFor("acct-1");

		Optional<OfferDelta> awayFill = change(durable, 3, TBOW, SOLD, 6_250, 127_500_000, 6_250, 20_400);
		assertTrue(awayFill.isPresent());
		assertEquals(OfferDelta.Side.SELL, awayFill.get().getSide());
		assertEquals(4_075, awayFill.get().getQuantity());
	}

	@Test
	public void reloadedBaselineDoesNotDoubleCountAnUnchangedOffer() {
		InMemorySnapshotStore store = new InMemorySnapshotStore();
		OfferTracker durable = new OfferTrackerImpl(store);
		durable.loadFor("acct-1");

		change(durable, 3, TBOW, SELLING, 0, 0, 6_250, 20_400);
		change(durable, 3, TBOW, SELLING, 2_175, 44_370_000, 6_250, 20_400);

		durable.reset();
		durable.loadFor("acct-1");

		assertFalse(change(durable, 3, TBOW, SELLING, 2_175, 44_370_000, 6_250, 20_400).isPresent());
	}

	@Test
	public void reloadIsScopedToTheLoggedInAccount() {
		InMemorySnapshotStore store = new InMemorySnapshotStore();
		OfferTracker durable = new OfferTrackerImpl(store);
		durable.loadFor("main");
		change(durable, 3, TBOW, SELLING, 0, 0, 6_250, 20_400);
		change(durable, 3, TBOW, SELLING, 2_175, 44_370_000, 6_250, 20_400);

		durable.reset();
		durable.loadFor("alt");

		assertFalse(change(durable, 3, 4151, SELLING, 900, 900_000, 1_000, 1_000).isPresent());
		Optional<OfferDelta> altFill = change(durable, 3, 4151, SELLING, 950, 950_000, 1_000, 1_000);
		assertTrue(altFill.isPresent());
		assertEquals(50, altFill.get().getQuantity());
	}
}
