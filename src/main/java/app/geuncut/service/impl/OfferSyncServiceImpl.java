package app.geuncut.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.GeOffer;
import app.geuncut.service.OfferSyncService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Keeps a per-slot view of the player's working offers and flushes the whole
 * set when it changes. A longer interval than the fill stream: this is a
 * glanceable mirror, not an audit log, and the coarse cadence keeps the plugin
 * well inside its request budget when a buy and a sell are filling at once.
 */
@Slf4j
@Singleton
public class OfferSyncServiceImpl extends AbstractSyncService implements OfferSyncService {
	private static final int FLUSH_SECONDS = 10;

	private final GeUncutApi api;
	private final Map<Integer, GeOffer> slots = new HashMap<>();

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
			dirty = false;
			flushInFlight = false;
		}
	}

	@Override
	public void record(int slot, int itemId, GrandExchangeOfferState state,
			int quantitySold, int totalQuantity, int price, Instant now) {
		synchronized (slots) {
			// Captured while a game event is firing, so it is always the hash of
			// the logged-in account even if the flush tick lands after a logout.
			lastAccountHash = accountHash();
			if (state == GrandExchangeOfferState.EMPTY) {
				if (slots.remove(slot) != null) {
					dirty = true;
				}
				return;
			}
			GeOffer offer = toOffer(slot, itemId, state, quantitySold, totalQuantity, price, now);
			// A malformed slot (non-positive total or price) is dropped rather
			// than queued: the server validates the whole snapshot, so one bad
			// row would otherwise reject every working offer.
			if (offer == null) {
				return;
			}
			// GE offers only fire an event when something actually changed, so
			// any recorded change is worth a flush; the interval caps the rate.
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
			// flushInFlight serializes the async posts: a slow/retrying request must
			// never be overtaken by a newer one, or a stale snapshot could land last.
			if (flushInFlight || !dirty || lastAccountHash == null) {
				return;
			}
			hash = lastAccountHash;
			snapshot = new ArrayList<>(slots.values());
			// Monotonic snapshot time so the server can reject an out-of-order post.
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
						// Not linked: retrying re-hits the same rejection every tick.
						log.warn("event=offer_sync_unauthorized offers={}", snapshot.size());
					} else {
						log.debug("event=offer_sync_requeued offers={} kind={} status={} reason=\"{}\"",
								snapshot.size(), failure.getKind(), failure.getStatusCode(), failure.getMessage());
					}
				});
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
