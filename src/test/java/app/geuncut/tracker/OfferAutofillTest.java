package app.geuncut.tracker;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferAutofillTest {
	@Test
	public void promptTitlesMapToKinds() {
		assertEquals(OfferAutofill.Prompt.PRICE, OfferAutofill.promptKind("Set a price for each item:"));
		assertEquals(OfferAutofill.Prompt.QUANTITY, OfferAutofill.promptKind("How many do you wish to buy?"));
		assertEquals(OfferAutofill.Prompt.QUANTITY, OfferAutofill.promptKind("How many do you wish to sell?"));
		assertNull(OfferAutofill.promptKind("Enter amount:"));
		assertNull(OfferAutofill.promptKind(null));
	}

	@Test
	public void priceChoicesOfferOurPriceAndBothAdjustments() {
		List<OfferAutofill.Choice> choices = OfferAutofill.choices(2512L, OfferAutofill.Prompt.PRICE, 2);
		assertEquals(3, choices.size());
		assertEquals("-2% 2,462", choices.get(0).getLabel());
		assertEquals(2462, choices.get(0).getValue());
		assertEquals("2,512", choices.get(1).getLabel());
		assertEquals(2512, choices.get(1).getValue());
		assertTrue(choices.get(1).isBase());
		assertEquals("+2% 2,562", choices.get(2).getLabel());
		assertEquals(2562, choices.get(2).getValue());
		assertFalse(choices.get(0).isBase());
		assertFalse(choices.get(2).isBase());
	}

	@Test
	public void zeroPercentKeepsTheSingleOriginalLine() {
		List<OfferAutofill.Choice> choices = OfferAutofill.choices(2512L, OfferAutofill.Prompt.PRICE, 0);
		assertEquals(1, choices.size());
		assertEquals("2,512", choices.get(0).getLabel());
		assertEquals(2512, choices.get(0).getValue());
		assertTrue(choices.get(0).isBase());
	}

	@Test
	public void quantityPromptsAreNeverAdjusted() {
		List<OfferAutofill.Choice> choices = OfferAutofill.choices(10_000L, OfferAutofill.Prompt.QUANTITY, 25);
		assertEquals(1, choices.size());
		assertEquals("10,000", choices.get(0).getLabel());
		assertEquals(10_000, choices.get(0).getValue());
	}

	@Test
	public void adjustedPricesRoundAndNeverFallBelowOne() {
		List<OfferAutofill.Choice> rounded = OfferAutofill.choices(1005L, OfferAutofill.Prompt.PRICE, 3);
		assertEquals(975, rounded.get(0).getValue());
		assertEquals(1035, rounded.get(2).getValue());
		List<OfferAutofill.Choice> tiny = OfferAutofill.choices(1L, OfferAutofill.Prompt.PRICE, 50);
		assertEquals(1, tiny.get(0).getValue());
		assertEquals(2, tiny.get(2).getValue());
	}

	@Test
	public void compactLabelsStayShortAtEveryPrice() {
		long[] prices = { 2_512L, 250_000L, 3_210_118L, 116_198_605L, 2_147_483_647L };
		for (long price : prices) {
			for (OfferAutofill.Choice choice : OfferAutofill.choices(price, OfferAutofill.Prompt.PRICE, 2, true)) {
				assertTrue(choice.getLabel() + " is too wide to render at price " + price,
						choice.getLabel().length() <= 12);
			}
		}
	}

	@Test
	public void compactFormattingKeepsSmallNumbersExact() {
		assertEquals("2,512", OfferAutofill.choices(2_512L, OfferAutofill.Prompt.PRICE, 0, true).get(0).getLabel());
		assertEquals("99,999", OfferAutofill.choices(99_999L, OfferAutofill.Prompt.PRICE, 0, true).get(0).getLabel());
	}

	@Test
	public void compactFormattingAbbreviatesTheBigOnes() {
		assertEquals("3.21M", OfferAutofill.choices(3_210_118L, OfferAutofill.Prompt.PRICE, 0, true).get(0).getLabel());
		assertEquals("2.15B", OfferAutofill.choices(2_147_483_647L, OfferAutofill.Prompt.PRICE, 0, true).get(0).getLabel());
		assertEquals("250K", OfferAutofill.choices(250_000L, OfferAutofill.Prompt.PRICE, 0, true).get(0).getLabel());
	}

	@Test
	public void compactChoicesStillCarryTheExactGp() {
		List<OfferAutofill.Choice> choices =
				OfferAutofill.choices(3_210_118L, OfferAutofill.Prompt.PRICE, 1, true);
		assertEquals(3_178_017L, choices.get(0).getValue());
		assertEquals(3_210_118L, choices.get(1).getValue());
		assertEquals(3_242_219L, choices.get(2).getValue());
	}

	@Test
	public void missingValuesProduceNoChoices() {
		assertTrue(OfferAutofill.choices(null, OfferAutofill.Prompt.PRICE, 2).isEmpty());
		assertTrue(OfferAutofill.choices(0L, OfferAutofill.Prompt.PRICE, 2).isEmpty());
	}
}
