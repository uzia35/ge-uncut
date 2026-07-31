package app.geuncut.service;

import java.time.Instant;
import java.util.List;

import app.geuncut.dto.GeOffer;
import app.geuncut.dto.OfferPlacement;
import net.runelite.api.GrandExchangeOfferState;

public interface OfferSyncService extends SyncService {
	void record(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int totalQuantity, int price, Instant now);

	List<GeOffer> current();

	void seed(List<OfferPlacement> placements);
}
