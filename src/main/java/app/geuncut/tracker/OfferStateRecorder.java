package app.geuncut.tracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.dto.OfferState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

@Slf4j
@Singleton
public class OfferStateRecorder {
	private final OfferLog offerLog;
	private final BuyLimitTracker buyLimits;
	private final Map<String, Map<Integer, OfferState>> lastBySlot = new HashMap<>();
	private final List<OfferState> held = new ArrayList<>();
	private String account;
	private String runId;
	private long nextSeq;
	private Instant lastObserved;

	@Inject
	public OfferStateRecorder(OfferLog offerLog, BuyLimitTracker buyLimits) {
		this.offerLog = offerLog;
		this.buyLimits = buyLimits;
	}

	public static OfferState stateOf(int slot, GrandExchangeOffer offer, Instant observedAt) {
		GrandExchangeOfferState state = offer.getState();
		if (state == null || state == GrandExchangeOfferState.EMPTY) {
			return OfferState.builder()
					.slot(slot)
					.state("empty")
					.itemId(0)
					.side(null)
					.priceEach(0)
					.quantityTotal(0)
					.quantityFilled(0)
					.spent(0)
					.observedAt(observedAt.toString())
					.build();
		}
		return OfferState.builder()
				.slot(slot)
				.state(stateName(state))
				.itemId(offer.getItemId())
				.side(sideOf(state))
				.priceEach(offer.getPrice())
				.quantityTotal(offer.getTotalQuantity())
				.quantityFilled(offer.getQuantitySold())
				.spent(offer.getSpent())
				.observedAt(observedAt.toString())
				.build();
	}

	static String stateName(GrandExchangeOfferState state) {
		switch (state) {
			case BUYING:
				return "buying";
			case BOUGHT:
				return "bought";
			case CANCELLED_BUY:
				return "cancelled_buy";
			case SELLING:
				return "selling";
			case SOLD:
				return "sold";
			case CANCELLED_SELL:
				return "cancelled_sell";
			default:
				return "empty";
		}
	}

	static String sideOf(GrandExchangeOfferState state) {
		switch (state) {
			case BUYING:
			case BOUGHT:
			case CANCELLED_BUY:
				return "buy";
			case SELLING:
			case SOLD:
			case CANCELLED_SELL:
				return "sell";
			default:
				return null;
		}
	}

	public synchronized void setInstallId(String installId) {
		String run = UUID.randomUUID().toString().substring(0, 8);
		this.runId = installId != null ? installId + "-" + run : run;
		this.nextSeq = 0;
	}

	public synchronized void record(OfferState state) {
		if (state == null) {
			return;
		}
		if (account == null) {
			held.add(state);
			return;
		}
		write(state);
	}

	public synchronized void onLoggedIn(long accountHash) {
		String known = accountHash != -1 ? Long.toString(accountHash) : null;
		if (known == null) {
			return;
		}
		account = known;
		List<OfferState> early = new ArrayList<>(held);
		held.clear();
		if (!early.isEmpty()) {
			log.debug("event=offer_states_released account={} count={}", known, early.size());
		}
		early.forEach(this::write);
	}

	public synchronized void reset() {
		account = null;
		held.clear();
	}

	private void write(OfferState state) {
		Map<Integer, OfferState> slots = lastBySlot.computeIfAbsent(account, key -> new HashMap<>());
		OfferState last = slots.get(state.getSlot());
		if (sameState(last, state)) {
			return;
		}
		OfferState stamped = state.toBuilder()
				.installId(runId)
				.seq(nextSeq++)
				.observedAt(notBefore(Instant.parse(state.getObservedAt())).toString())
				.build();
		if (!offerLog.append(account, stamped)) {
			return;
		}
		slots.put(state.getSlot(), stamped);
		trackBuyLimit(last, stamped);
		log.debug("event=offer_state_recorded slot={} state={} item={} filled={} total={}",
				state.getSlot(), state.getState(), state.getItemId(), state.getQuantityFilled(),
				state.getQuantityTotal());
	}

	private Instant notBefore(Instant observed) {
		if (lastObserved != null && observed.isBefore(lastObserved)) {
			return lastObserved;
		}
		lastObserved = observed;
		return observed;
	}

	private void trackBuyLimit(OfferState last, OfferState state) {
		if (last == null || !"buy".equals(state.getSide())) {
			return;
		}
		int rise = sameOffer(last, state) && state.getQuantityFilled() >= last.getQuantityFilled()
				? state.getQuantityFilled() - last.getQuantityFilled()
				: state.getQuantityFilled();
		if (rise > 0) {
			buyLimits.recordBuy(state.getItemId(), rise, Instant.parse(state.getObservedAt()));
		}
	}

	private static boolean sameOffer(OfferState a, OfferState b) {
		return a.getItemId() == b.getItemId()
				&& a.getPriceEach() == b.getPriceEach()
				&& a.getQuantityTotal() == b.getQuantityTotal()
				&& Objects.equals(a.getSide(), b.getSide());
	}

	private static boolean sameState(OfferState a, OfferState b) {
		return a != null
				&& sameOffer(a, b)
				&& a.getQuantityFilled() == b.getQuantityFilled()
				&& a.getSpent() == b.getSpent()
				&& Objects.equals(a.getState(), b.getState());
	}
}
