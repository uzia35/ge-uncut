package app.geuncut.tracker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeTaxTest {
	@Test
	public void itemsUnderFiftyCoinsAreExempt() {
		assertEquals(0, GeTax.perItem(1));
		assertEquals(0, GeTax.perItem(49));
		assertEquals(49, GeTax.afterTax(49));
		assertEquals(49, GeTax.beforeTax(49));
	}

	@Test
	public void theRateIsTwoPercentFloored() {
		assertEquals(1, GeTax.perItem(50));
		assertEquals(2, GeTax.perItem(100));
		assertEquals(2, GeTax.perItem(149));
		assertEquals(3, GeTax.perItem(150));
		assertEquals(420, GeTax.perItem(21_000));
		assertEquals(20_580, GeTax.afterTax(21_000));
	}

	@Test
	public void theChargeIsCappedAtFiveMillionPerItem() {
		assertEquals(5_000_000, GeTax.perItem(250_000_000));
		assertEquals(5_000_000, GeTax.perItem(2_000_000_000));
		assertEquals(295_000_000, GeTax.afterTax(300_000_000));
	}

	@Test
	public void aDisplayedPriceInvertsBackToTheOfferPrice() {
		for (int price : new int[] { 60, 1_000, 21_000, 1_000_000, 1_500_000_000 }) {
			int shown = GeTax.afterTax(price);
			int recovered = GeTax.beforeTax(shown);
			assertTrue("price " + price + " recovered as " + recovered,
					Math.abs((long) recovered - price) <= 1);
			assertEquals("the recovered price must show the same figure in game",
					shown, GeTax.afterTax(recovered));
		}
	}

	@Test
	public void aCappedSellPriceInvertsExactly() {
		assertEquals(300_000_000, GeTax.beforeTax(295_000_000));
	}
}
