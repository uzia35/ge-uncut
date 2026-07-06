package app.geuncut.service;

import java.time.Instant;
import java.util.List;

import app.geuncut.dto.GeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Mirrors the player's live GE slots to geuncut.app. Unlike the fill stream
 * this is current state, not an event log: the latest snapshot of every
 * occupied slot is flushed as a whole, so a cancel or a collect drops the slot
 * for free on the next flush.
 */
public interface OfferSyncService extends SyncService {
	void record(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int totalQuantity, int price, Instant now);

	/** Current working offers, for the in-client panel. */
	List<GeOffer> current();
}
