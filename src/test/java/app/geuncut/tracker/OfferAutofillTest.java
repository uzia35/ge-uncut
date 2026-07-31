package app.geuncut.tracker;

import java.util.List;

import app.geuncut.dto.Flip;
import app.geuncut.dto.Position;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfferAutofillTest {
	private static final Flip FINDER = Flip.builder()
			.itemId(1623).name("Uncut diamond").buyPrice(2512).targetSellPrice(2617).quantity(10_000).build();

	private static final Position TRACKED = Position.builder()
			.id(42).itemId(22124).name("Superior dragon bones").accountHash("acct-a")
			.quantity(1097).buyPrice(20_065).soldQty(400L).targetSellPrice(21_500L).build();

	@Test
	public void promptTitlesMapToKinds() {
		assertEquals(OfferAutofill.Prompt.PRICE, OfferAutofill.promptKind("Set a price for each item:"));
		assertEquals(OfferAutofill.Prompt.QUANTITY, OfferAutofill.promptKind("How many do you wish to buy?"));
		assertEquals(OfferAutofill.Prompt.QUANTITY, OfferAutofill.promptKind("How many do you wish to sell?"));
		assertNull(OfferAutofill.promptKind("Enter amount:"));
		assertNull(OfferAutofill.promptKind(null));
	}

	@Test
	public void buyOffersTakeTheFinderCardsNumbers() {
		assertEquals(Long.valueOf(2512), OfferAutofill.resolve(1623, false, OfferAutofill.Prompt.PRICE,
				List.of(FINDER), List.of(), "acct-a"));
		assertEquals(Long.valueOf(10_000), OfferAutofill.resolve(1623, false, OfferAutofill.Prompt.QUANTITY,
				List.of(FINDER), List.of(), "acct-a"));
		assertNull(OfferAutofill.resolve(4151, false, OfferAutofill.Prompt.PRICE,
				List.of(FINDER), List.of(), "acct-a"));
	}

	@Test
	public void sellOffersTakeTheTrackedFlipsTargetAndRemainder() {
		assertEquals(Long.valueOf(21_500), OfferAutofill.resolve(22124, true, OfferAutofill.Prompt.PRICE,
				List.of(), List.of(TRACKED), "acct-a"));
		assertEquals(Long.valueOf(697), OfferAutofill.resolve(22124, true, OfferAutofill.Prompt.QUANTITY,
				List.of(), List.of(TRACKED), "acct-a"));
		assertNull(OfferAutofill.resolve(1623, true, OfferAutofill.Prompt.PRICE,
				List.of(FINDER), List.of(), "acct-a"));
	}

	@Test
	public void sellsRespectTheAccountAndMissingData() {
		assertNull(OfferAutofill.resolve(22124, true, OfferAutofill.Prompt.PRICE,
				List.of(), List.of(TRACKED), "acct-b"));
		Position noTarget = Position.builder().id(7).itemId(2).name("Cannonball").quantity(100).buyPrice(180).build();
		assertNull(OfferAutofill.resolve(2, true, OfferAutofill.Prompt.PRICE, List.of(), List.of(noTarget), null));
		Position soldOut = Position.builder().id(8).itemId(3).name("Feather").quantity(100)
				.buyPrice(2).soldQty(100L).targetSellPrice(3L).build();
		assertNull(OfferAutofill.resolve(3, true, OfferAutofill.Prompt.QUANTITY, List.of(), List.of(soldOut), null));
	}
}
