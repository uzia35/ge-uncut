package app.geuncut.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.OfferPlacement;
import app.geuncut.service.OfferSyncService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;

@Slf4j
@Singleton
public class OfferSyncServiceImpl extends AbstractSyncService implements OfferSyncService {
	private static final int FLUSH_SECONDS = 10;

	private final GeUncutApi api;
	private final Map<Integer, GeOffer> slots = new HashMap<>();
	private final Map<String, OfferPlacement> seeds = new HashMap<>();

	private String lastAccountHash;
	private boolean dirty;
	private boolean flushInFlight;

	@Inject
	public OfferSyncServiceImpl(GeUncutApi api, ScheduledExecutorService executor) {
		super(executor, FLUSH_SECONDS);
		this.api = api;
	}

	@Override
	protected void onStop() {
		flush();
		synchronized (slots) {
			slots.clear();
			seeds.clear();
			dirty = false;
			flushInFlight = false;
		}
	}

	@Override
	public void seed(List<OfferPlacement> placements) {
		synchronized (slots) {
			for (OfferPlacement placement : placements) {
				if (placement.getAccountHash() == null || placement.getPlacedAt() == null) {
					continue;
				}
				seeds.put(placement.getAccountHash() + "|" + placement.getSlot(), placement);
				if (placement.getAccountHash().equals(lastAccountHash)) {
					GeOffer existing = slots.get(placement.getSlot());
					String placed = seededPlacement(placement, existing);
					if (placed != null && !placed.equals(existing.getOccurredAt())) {
						slots.put(placement.getSlot(), withOccurredAt(existing, placed));
						dirty = true;
					}
				}
			}
		}
	}

	@Override
	public void record(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int totalQuantity, int price, Instant now) {
		synchronized (slots) {
			lastAccountHash = accountHash();
			if (state == GrandExchangeOfferState.EMPTY) {
				if (slots.remove(slot) != null) {
					dirty = true;
				}
				return;
			}
			GeOffer offer = toOffer(slot, itemId, state, quantitySold, totalQuantity, price, now);
			if (offer == null) {
				return;
			}
			GeOffer previous = slots.get(slot);
			if (previous != null && previous.getItemId() == offer.getItemId()
					&& previous.getSide().equals(offer.getSide())
					&& previous.getPriceEach() == offer.getPriceEach()
					&& previous.getQuantityTotal() == offer.getQuantityTotal()
					&& previous.getOccurredAt() != null) {
				offer = withOccurredAt(offer, previous.getOccurredAt());
			} else if (previous == null && lastAccountHash != null) {
				OfferPlacement seedRow = seeds.remove(lastAccountHash + "|" + slot);
				String placed = seedRow != null ? seededPlacement(seedRow, offer) : null;
				if (placed != null) {
					offer = withOccurredAt(offer, placed);
				}
			}
			slots.put(slot, offer);
			dirty = true;
		}
	}

	@Override
	public List<GeOffer> current() {
		synchronized (slots) {
			return new ArrayList<>(slots.values());
		}
	}

	@Override
	protected void flush() {
		String hash;
		List<GeOffer> snapshot;
		String syncedAt;
		synchronized (slots) {
			if (flushInFlight || !dirty || lastAccountHash == null) {
				return;
			}
			hash = lastAccountHash;
			snapshot = new ArrayList<>(slots.values());
			syncedAt = Instant.now().toString();
			dirty = false;
			flushInFlight = true;
		}
		api.postOffers(hash, snapshot, syncedAt,
				() -> {
					synchronized (slots) {
						flushInFlight = false;
					}
					log.debug("event=offer_sync_flushed count={}", snapshot.size());
				},
				failure -> {
					synchronized (slots) {
						flushInFlight = false;
						if (!failure.isUnauthorized()) {
							dirty = true;
						}
					}
					if (failure.isUnauthorized()) {
						log.warn("event=offer_sync_unauthorized offers={}", snapshot.size());
					} else {
						log.debug("event=offer_sync_requeued offers={} kind={} status={} reason=\"{}\"",
								snapshot.size(), failure.getKind(), failure.getStatusCode(), failure.getMessage());
					}
				});
	}

	private static String seededPlacement(OfferPlacement placement, GeOffer offer) {
		if (offer == null || placement.getSide() == null
				|| placement.getItemId() != offer.getItemId()
				|| !placement.getSide().equals(offer.getSide())
				|| placement.getPriceEach() != offer.getPriceEach()
				|| placement.getQuantityTotal() != offer.getQuantityTotal()
				|| placement.getQuantityFilled() > offer.getQuantityFilled()) {
			return null;
		}
		return toInstantString(placement.getPlacedAt());
	}

	private static String toInstantString(String timestamp) {
		try {
			return Instant.parse(timestamp).toString();
		} catch (RuntimeException notInstant) {
			try {
				return LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC).toString();
			} catch (RuntimeException unparseable) {
				return null;
			}
		}
	}

	private static GeOffer withOccurredAt(GeOffer offer, String occurredAt) {
		return GeOffer.builder()
				.slot(offer.getSlot())
				.itemId(offer.getItemId())
				.side(offer.getSide())
				.state(offer.getState())
				.quantityFilled(offer.getQuantityFilled())
				.quantityTotal(offer.getQuantityTotal())
				.priceEach(offer.getPriceEach())
				.occurredAt(occurredAt)
				.build();
	}

	private static GeOffer toOffer(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int totalQuantity, int price, Instant now) {
		if (totalQuantity <= 0 || price <= 0 || quantitySold < 0 || quantitySold > totalQuantity) {
			return null;
		}
		String side;
		String stateName;
		switch (state) {
			case BUYING:
				side = "buy";
				stateName = "buying";
				break;
			case BOUGHT:
				side = "buy";
				stateName = "bought";
				break;
			case CANCELLED_BUY:
				side = "buy";
				stateName = "cancelled_buy";
				break;
			case SELLING:
				side = "sell";
				stateName = "selling";
				break;
			case SOLD:
				side = "sell";
				stateName = "sold";
				break;
			case CANCELLED_SELL:
				side = "sell";
				stateName = "cancelled_sell";
				break;
			default:
				return null;
		}
		return GeOffer.builder()
				.slot(slot)
				.itemId(itemId)
				.side(side)
				.state(stateName)
				.quantityFilled(quantitySold)
				.quantityTotal(totalQuantity)
				.priceEach(price)
				.occurredAt(now.toString())
				.build();
	}
}
