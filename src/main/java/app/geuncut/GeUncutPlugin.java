package app.geuncut;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import app.geuncut.api.GeUncutApi;
import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.GeOffer;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.FlipsService;
import app.geuncut.service.impl.FlipsServiceImpl;
import app.geuncut.service.impl.LinkServiceImpl;
import app.geuncut.service.impl.OfferSyncServiceImpl;
import app.geuncut.service.impl.PositionsServiceImpl;
import app.geuncut.service.impl.TradeSyncServiceImpl;
import app.geuncut.service.LinkService;
import app.geuncut.service.OfferSyncService;
import app.geuncut.service.PositionsService;
import app.geuncut.service.TradeSyncService;
import app.geuncut.tracker.BuyLimitTracker;
import app.geuncut.tracker.impl.BuyLimitTrackerImpl;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import app.geuncut.tracker.OfferTracker;
import app.geuncut.ui.FlipsPanel;
import app.geuncut.ui.ItemIconLoader;
import com.google.inject.Binder;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(name = "GE Uncut", description = "Live flip finder with automatic profit tracking, backed by geuncut.app", tags = {
		"grand exchange", "flipping", "money making", "ge", "merch" })
public class GeUncutPlugin extends Plugin {
	@Override
	public void configure(Binder binder) {
		binder.bind(GeUncutApi.class).to(HttpGeUncutApi.class);
		binder.bind(FlipsService.class).to(FlipsServiceImpl.class);
		binder.bind(PositionsService.class).to(PositionsServiceImpl.class);
		binder.bind(LinkService.class).to(LinkServiceImpl.class);
		binder.bind(TradeSyncService.class).to(TradeSyncServiceImpl.class);
		binder.bind(OfferSyncService.class).to(OfferSyncServiceImpl.class);
		binder.bind(OfferTracker.class).to(OfferTrackerImpl.class);
		binder.bind(BuyLimitTracker.class).to(BuyLimitTrackerImpl.class);
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private GeUncutConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private FlipsService flips;

	@Inject
	private PositionsService positions;

	@Inject
	private LinkService link;

	@Inject
	private TradeSyncService tradeSync;

	@Inject
	private OfferSyncService offerSync;

	@Inject
	private OfferTracker offerTracker;

	@Inject
	private BuyLimitTracker buyLimits;

	private FlipsPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() {
		panel = new FlipsPanel(this::refreshFlips, this::linkAccount,
				new ItemIconLoader(okHttpClient, itemManager), this::openItem);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/geuncut_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("GE Uncut")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		tradeSync.start(() -> Long.toString(client.getAccountHash()));
		offerSync.start(() -> Long.toString(client.getAccountHash()));
		refreshFlips();
	}

	@Override
	protected void shutDown() {
		link.cancel();
		tradeSync.stop();
		offerSync.stop();
		clientToolbar.removeNavigation(navButton);
		// The trackers are only ever mutated on the client thread; hop the resets
		// there too so shutDown (which may run off it) can't race an offer event.
		clientThread.invoke(() -> {
			offerTracker.reset();
			buyLimits.reset();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
			offerTracker.reset();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
		GrandExchangeOffer offer = event.getOffer();
		Instant now = Instant.now();
		offerTracker.onOfferChanged(
				event.getSlot(),
				offer.getItemId(),
				offer.getState(),
				offer.getQuantitySold(),
				offer.getSpent(),
				now).ifPresent(this::onFill);
		offerSync.record(
				event.getSlot(),
				offer.getItemId(),
				offer.getState(),
				offer.getQuantitySold(),
				offer.getTotalQuantity(),
				offer.getPrice(),
				now);
		// Resolve names here on the client thread; the panel only has item ids.
		List<GeOffer> working = offerSync.current();
		Map<Integer, String> itemNames = new HashMap<>();
		for (GeOffer openOffer : working) {
			itemNames.computeIfAbsent(openOffer.getItemId(), this::itemName);
		}
		SwingUtilities.invokeLater(() -> panel.showOffers(working, itemNames));
	}

	private String itemName(int itemId) {
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition != null ? composition.getName() : "Item " + itemId;
	}

	@Provides
	GeUncutConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GeUncutConfig.class);
	}

	private void onFill(OfferDelta delta) {
		log.debug("event=fill_observed item={} side={} quantity={} price_each={} slot={}",
				delta.getItemId(), delta.getSide(), delta.getQuantity(), delta.getPriceEach(), delta.getSlot());
		if (delta.getSide() == OfferDelta.Side.BUY) {
			buyLimits.recordBuy(delta.getItemId(), delta.getQuantity(), delta.getOccurredAt());
		}
		tradeSync.accept(delta);
	}

	private void refreshFlips() {
		// Capture the panel so a fetch that resolves after a disable/re-enable can't
		// write its stale result into the fresh panel from the next startUp.
		FlipsPanel target = panel;
		boolean linked = !config.apiToken().trim().isEmpty();
		SwingUtilities.invokeLater(() -> target.showStatus("Scanning..."));
		refreshPositions();
		flips.fetch(target.selectedScan(), target.selectedRisk(),
				list -> SwingUtilities.invokeLater(() -> {
					if (target != panel) {
						return;
					}
					target.showFlips(list, linked);
				}),
				failure -> SwingUtilities.invokeLater(() -> {
					if (target != panel) {
						return;
					}
					// Unauthorized means unlinked (never paired, revoked, or the
					// token aged out): offer pairing instead of an error message.
					if (failure.isUnauthorized()) {
						target.showLinkPrompt();
					} else {
						target.showStatus(failure.getMessage());
					}
				}));
	}

	private void openItem(int itemId) {
		LinkBrowser.browse(config.apiBase() + "/items/" + itemId);
	}

	private void refreshPositions() {
		// Best-effort: an unlinked or offline fetch leaves the last known flips in
		// place; the flip scan above is what surfaces the link prompt.
		FlipsPanel target = panel;
		positions.fetch(
				response -> SwingUtilities.invokeLater(() -> {
					if (target == panel) {
						target.showActiveFlips(response);
					}
				}),
				failure -> log.debug("event=positions_fetch_failed kind={} status={}",
						failure.getKind(), failure.getStatusCode()));
	}

	private void linkAccount() {
		if (link.isPairing()) {
			link.openClaimPage();
			return;
		}
		link.begin(
				code -> SwingUtilities.invokeLater(() -> panel.showLinkCode(code)),
				// onLinked fires on an OkHttp thread; hop to the EDT before refreshFlips
				// reads the panel's picker state, as the other two callbacks already do.
				() -> SwingUtilities.invokeLater(this::refreshFlips),
				failure -> SwingUtilities.invokeLater(() -> panel.showStatus(failure.getMessage())));
	}
}
