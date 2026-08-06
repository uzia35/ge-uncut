package app.geuncut;

import java.util.List;

import app.geuncut.tracker.OfferAutofill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfferRowFitTest {
	private static final int AVAILABLE = 519 - 30;

	private static final long[] PRICES = { 2_512L, 250_000L, 3_210_118L, 116_198_605L, 2_147_483_647L };

	private static List<OfferAutofill.Choice> rendered(long price, int percent) {
		List<OfferAutofill.Choice> all = OfferAutofill.choices(price, OfferAutofill.Prompt.PRICE, percent);
		if (!GeUncutPlugin.fits(all, AVAILABLE)) {
			List<OfferAutofill.Choice> compact =
					OfferAutofill.choices(price, OfferAutofill.Prompt.PRICE, percent, true);
			if (GeUncutPlugin.fits(compact, AVAILABLE)) {
				all = compact;
			}
		}
		return GeUncutPlugin.fitChoices(all, AVAILABLE);
	}

	@Test
	public void allThreePricesRenderAtEveryMagnitude() {
		for (long price : PRICES) {
			for (int percent : new int[] { 1, 2, 5, 10, 50 }) {
				List<OfferAutofill.Choice> shown = rendered(price, percent);
				assertEquals("price " + price + " at " + percent + "% lost a tip", 3, shown.size());
				assertTrue("row overflows at price " + price + " / " + percent + "%",
						GeUncutPlugin.fits(shown, AVAILABLE));
			}
		}
	}

	@Test
	public void theClickedValueIsExactEvenWhenTheLabelIsAbbreviated() {
		List<OfferAutofill.Choice> shown = rendered(2_147_483_647L, 2);
		assertEquals(2_104_533_974L, shown.get(0).getValue());
		assertEquals(2_147_483_647L, shown.get(1).getValue());
		assertEquals(2_190_433_320L, shown.get(2).getValue());
	}

	@Test
	public void smallPricesKeepTheirExactDigits() {
		for (OfferAutofill.Choice choice : rendered(2_512L, 2)) {
			assertTrue("unexpectedly abbreviated: " + choice.getLabel(),
					!choice.getLabel().endsWith("K") && !choice.getLabel().endsWith("M"));
		}
	}

	@Test
	public void aNarrowRowShedsOneTipBeforeFallingBackToOne() {
		List<OfferAutofill.Choice> all =
				OfferAutofill.choices(2_147_483_647L, OfferAutofill.Prompt.PRICE, 50, true);
		assertEquals(3, GeUncutPlugin.fitChoices(all, 400).size());
		assertEquals(2, GeUncutPlugin.fitChoices(all, 250).size());
		assertEquals(1, GeUncutPlugin.fitChoices(all, 100).size());
	}
}
