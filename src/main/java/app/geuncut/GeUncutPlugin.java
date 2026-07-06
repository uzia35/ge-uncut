package app.geuncut;

import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import app.geuncut.api.GeUncutApi;
import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.model.OfferDelta;
import app.geuncut.service.FlipsService;
import app.geuncut.service.impl.FlipsServiceImpl;
import app.geuncut.service.impl.LinkServiceImpl;
import app.geuncut.service.impl.TradeSyncServiceImpl;
import app.geuncut.service.LinkService;
import app.geuncut.service.TradeSyncService;
import app.geuncut.tracker.BuyLimitTracker;
import app.geuncut.tracker.impl.BuyLimitTrackerImpl;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import app.geuncut.tracker.OfferTracker;
import app.geuncut.ui.FlipsPanel;
import com.google.inject.Binder;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(name = "GE Uncut", description = "Live flip finder with automatic profit tracking, backed by geuncut.app", tags = {
		"grand exchange", "flipping", "money making", "ge", "merch" })
public class GeUncutPlugin extends Plugin {
	@Override
	public void configure(Binder binder) {
		binder.bind(GeUncutApi.class).to(HttpGeUncutApi.class);
		binder.bind(FlipsService.class).to(FlipsServiceImpl.class);
		binder.bind(LinkService.class).to(LinkServiceImpl.class);
		binder.bind(TradeSyncService.class).to(TradeSyncServiceImpl.class);
		binder.bind(OfferTracker.class).to(OfferTrackerImpl.class);
		binder.bind(BuyLimitTracker.class).to(BuyLimitTrackerImpl.class);
	}

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private FlipsService flips;

	@Inject
	private LinkService link;

	@Inject
	private TradeSyncService tradeSync;

	@Inject
	private OfferTracker offerTracker;

	@Inject
	private BuyLimitTracker buyLimits;

	private FlipsPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() {
		panel = new FlipsPanel(this::refreshFlips, this::linkAccount);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/geuncut_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("GE Uncut")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		tradeSync.start(() -> Long.toString(client.getAccountHash()));
		refreshFlips();
	}

	@Override
	protected void shutDown() {
		link.cancel();
		tradeSync.stop();
		clientToolbar.removeNavigation(navButton);
		offerTracker.reset();
		buyLimits.reset();
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
		offerTracker.onOfferChanged(
				event.getSlot(),
				offer.getItemId(),
				offer.getState(),
				offer.getQuantitySold(),
				offer.getSpent(),
				Instant.now()).ifPresent(this::onFill);
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
		SwingUtilities.invokeLater(() -> panel.showStatus("Scanning..."));
		flips.fetch(panel.selectedScan(),
				list -> SwingUtilities.invokeLater(() -> panel.showFlips(list)),
				failure -> SwingUtilities.invokeLater(() -> {
					// Unauthorized means unlinked (never paired, revoked, or the
					// token aged out): offer pairing instead of an error message.
					if (failure.isUnauthorized()) {
						panel.showLinkPrompt();
					} else {
						panel.showStatus(failure.getMessage());
					}
				}));
	}

	private void linkAccount() {
		if (link.isPairing()) {
			link.openClaimPage();
			return;
		}
		link.begin(
				code -> SwingUtilities.invokeLater(() -> panel.showLinkCode(code)),
				this::refreshFlips,
				failure -> SwingUtilities.invokeLater(() -> panel.showStatus(failure.getMessage())));
	}
}
