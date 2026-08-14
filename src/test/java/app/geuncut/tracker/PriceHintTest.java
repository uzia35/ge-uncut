package app.geuncut.tracker;

import java.time.Instant;

import app.geuncut.dto.ItemPrice;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceHintTest {
	private static final Instant NOW = Instant.parse("2026-08-14T16:00:00Z");

	@Test
	public void sidePricePicksTheRelevantLeg() {
		ItemPrice price = new ItemPrice(536, 3131L, 3168L, "2026-08-14T15:54:35", "2026-08-14T15:54:40");
		assertEquals(3131L, PriceHint.sidePrice(price, false));
		assertEquals(3168L, PriceHint.sidePrice(price, true));
		assertEquals(0L, PriceHint.sidePrice(null, false));
	}

	@Test
	public void missingLegsAreNotUsable() {
		assertFalse(PriceHint.hasPrices(null));
		assertFalse(PriceHint.hasPrices(new ItemPrice(1, null, 10L, null, null)));
		assertFalse(PriceHint.hasPrices(new ItemPrice(1, 10L, null, null, null)));
		assertFalse(PriceHint.hasPrices(new ItemPrice(1, 0L, 10L, null, null)));
		assertTrue(PriceHint.hasPrices(new ItemPrice(1, 5L, 10L, null, null)));
	}

	@Test
	public void agoReadsNaiveUtcTimestampsAsASingleUnit() {
		assertEquals("now", PriceHint.ago("2026-08-14T15:59:30", NOW));
		assertEquals("3m ago", PriceHint.ago("2026-08-14T15:57:00", NOW));
		assertEquals("2h ago", PriceHint.ago("2026-08-14T14:00:00", NOW));
		assertEquals("2h ago", PriceHint.ago("2026-08-14T13:55:00", NOW));
		assertEquals("2d ago", PriceHint.ago("2026-08-12T15:00:00", NOW));
		assertEquals("?", PriceHint.ago(null, NOW));
	}

	@Test
	public void agoToleratesATrailingZone() {
		assertEquals("3m ago", PriceHint.ago("2026-08-14T15:57:00Z", NOW));
	}
}
