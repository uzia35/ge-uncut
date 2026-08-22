package app.geuncut.tracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeTradeEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeHistoryDiffTest {
	private static final int TBOW = 20997;
	private static final int FEATHER = 314;

	private static GeHistoryRow row(int itemId, String side, int quantity, int priceEach) {
		return GeHistoryRow.builder().itemId(itemId).side(side).quantity(quantity).priceEach(priceEach).build();
	}

	private static GeTradeEvent entry(String offerId, int itemId, String side, int quantity, int priceEach) {
		return GeTradeEvent.builder()
				.accountHash("acct-1")
				.idempotencyKey("acct-1:0:" + offerId + ":" + quantity)
				.itemId(itemId).side(side).quantity(quantity).priceEach(priceEach).slot(0)
				.occurredAt("2026-07-05T12:00:00Z")
				.offerInstanceId(offerId).source("live")
				.build();
	}

	@Test
	public void aRowTheLogAlreadyKnowsIsNotUnseen() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "buy", 8, 1_000_000));
		assertTrue(GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "buy", 8, 1_000_000)), logged).isEmpty());
	}

	@Test
	public void aRowTheLogHasNeverSeenIsReturned() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "buy", 8, 1_000_000));
		List<GeHistoryRow> unseen = GeHistoryDiff.unseen(
				Arrays.asList(row(FEATHER, "buy", 1_000, 3)), logged);
		assertEquals(1, unseen.size());
		assertEquals(FEATHER, unseen.get(0).getItemId());
	}

	@Test
	public void oneLoggedOfferCanOnlyAccountForOneRow() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "buy", 8, 1_000_000));
		List<GeHistoryRow> rows = Arrays.asList(
				row(TBOW, "buy", 8, 1_000_000),
				row(TBOW, "buy", 8, 1_000_000));

		assertEquals(1, GeHistoryDiff.unseen(rows, logged).size());
	}

	@Test
	public void severalFillsOfOneOfferAddUpToTheRowTheGameShows() {
		List<GeTradeEvent> logged = Arrays.asList(
				entry("a", TBOW, "buy", 3, 1_000_000),
				entry("a", TBOW, "buy", 5, 1_004_000));

		assertTrue(GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "buy", 8, 1_002_500)), logged).isEmpty());
	}

	@Test
	public void aRoundingGapOfOneCoinStillMatches() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "buy", 8, 1_000_000));
		assertTrue(GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "buy", 8, 999_999)), logged).isEmpty());
		assertEquals(1, GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "buy", 8, 999_990)), logged).size());
	}

	@Test
	public void aSellRowShownAfterTaxMatchesTheOfferPriceInTheLog() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "sell", 100, 21_000));
		assertTrue(GeHistoryDiff.unseen(
				Arrays.asList(row(TBOW, "sell", 100, GeTax.afterTax(21_000))), logged).isEmpty());
	}

	@Test
	public void aTaxExemptSellRowMatchesAtItsFaceValue() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", FEATHER, "sell", 1_000, 3));
		assertTrue(GeHistoryDiff.unseen(Arrays.asList(row(FEATHER, "sell", 1_000, 3)), logged).isEmpty());
	}

	@Test
	public void aCappedSellRowMatchesTheOfferPriceInTheLog() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "sell", 1, 300_000_000));
		assertTrue(GeHistoryDiff.unseen(
				Arrays.asList(row(TBOW, "sell", 1, 295_000_000)), logged).isEmpty());
	}

	@Test
	public void offersAlreadyImportedFromHistoryCountAsSeen() {
		GeTradeEvent imported = entry("h1", TBOW, "sell", 100, 21_000).toBuilder()
				.source("history").build();
		assertTrue(GeHistoryDiff.unseen(
				Arrays.asList(row(TBOW, "sell", 100, GeTax.afterTax(21_000))),
				Arrays.asList(imported)).isEmpty());
	}

	@Test
	public void entriesWithNoOfferIdAreGroupedByItemAndSide() {
		List<GeTradeEvent> logged = Arrays.asList(
				entry("a", TBOW, "buy", 3, 1_000_000).toBuilder().offerInstanceId(null).build(),
				entry("b", TBOW, "buy", 5, 1_000_000).toBuilder().offerInstanceId(null).build());

		assertTrue(GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "buy", 8, 1_000_000)), logged).isEmpty());
	}

	@Test
	public void anEmptyLogLeavesEveryRowUnseen() {
		List<GeHistoryRow> rows = Arrays.asList(
				row(TBOW, "buy", 8, 1_000_000),
				row(FEATHER, "sell", 1_000, 3));

		assertEquals(2, GeHistoryDiff.unseen(rows, new ArrayList<>()).size());
		assertEquals(2, GeHistoryDiff.unseen(rows, null).size());
		assertTrue(GeHistoryDiff.unseen(Collections.emptyList(), new ArrayList<>()).isEmpty());
	}

	@Test
	public void aBuyAndASellOfTheSameItemDoNotSatisfyEachOther() {
		List<GeTradeEvent> logged = Arrays.asList(entry("a", TBOW, "buy", 8, 1_000_000));
		assertEquals(1, GeHistoryDiff.unseen(Arrays.asList(row(TBOW, "sell", 8, 1_000_000)), logged).size());
	}
}
