package app.geuncut;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import app.geuncut.api.GeUncutApi;
import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.Flip;
import app.geuncut.dto.GeHistoryRow;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.Position;
import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.OfferAutofill;
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
import app.geuncut.tracker.GeHistoryParser;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemComposition;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
	private GeUncutApi api;

	@Inject
	private GeUncutConfig config;

	@Inject
	private ConfigManager configManager;

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

	@Inject
	private ScheduledExecutorService executor;

	private FlipsPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> positionsPoll;
	private boolean offerReplayPending;
	private volatile List<Flip> latestFlips = List.of();
	private volatile List<Position> latestPositions = List.of();
	private String lastAutofillPrompt;
	private Widget offerLine;

	private static final int POSITIONS_POLL_SECONDS = 30;
	private static final int POST_FILL_REFRESH_SECONDS = 8;

	@Override
	protected void startUp() {
		installPanel();

		tradeSync.start(() -> Long.toString(client.getAccountHash()));
		offerSync.start(() -> linked() ? Long.toString(client.getAccountHash()) : null);
		positionsPoll = executor.scheduleWithFixedDelay(this::refreshPositions,
				POSITIONS_POLL_SECONDS, POSITIONS_POLL_SECONDS, TimeUnit.SECONDS);
		refreshFlips();
		seedOfferPlacements();
	}

	private void seedOfferPlacements() {
		api.fetchOfferPlacements(
				placements -> {
					offerSync.seed(placements);
					clientThread.invokeLater(this::pushOffers);
				},
				failure -> log.debug("event=offer_placements_fetch_failed kind={} status={}",
						failure.getKind(), failure.getStatusCode()));
	}

	private void installPanel() {
		FlipsPanel.applyTheme(config.lightMode());
		panel = new FlipsPanel(this::refreshFlips, this::linkAccount, this::unlinkAccount, this::openMovers,
				this::openMyFlips, new ItemIconLoader(okHttpClient, itemManager), this::openItem, this::markNotFlip,
				this::restoreFlip, this::trackPair);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/geuncut_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("GE Uncut")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!GeUncutConfig.GROUP.equals(event.getGroup()) || !"lightMode".equals(event.getKey())) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			clientToolbar.removeNavigation(navButton);
			installPanel();
			refreshFlips();
			refreshMovers();
			refreshPositions();
		});
	}

	@Override
	protected void shutDown() {
		link.cancel();
		if (positionsPoll != null) {
			positionsPoll.cancel(false);
			positionsPoll = null;
		}
		tradeSync.stop();
		offerSync.stop();
		clientToolbar.removeNavigation(navButton);
		clientThread.invoke(() -> {
			offerTracker.reset();
			buyLimits.reset();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		GameState current = event.getGameState();
		if (current == GameState.LOGIN_SCREEN || current == GameState.HOPPING
				|| current == GameState.CONNECTION_LOST) {
			offerReplayPending = true;
		}
		if (current == GameState.LOGIN_SCREEN || current == GameState.HOPPING) {
			offerTracker.reset();
		}
		if (current == GameState.LOGGED_IN) {
			SwingUtilities.invokeLater(() -> panel.setGameActive(true));
			if (offerReplayPending) {
				offerReplayPending = false;
				seedOfferPlacements();
			}
		} else if (current == GameState.LOGIN_SCREEN) {
			SwingUtilities.invokeLater(() -> panel.setGameActive(false));
		}
	}

	private void unlinkAccount() {
		link.cancel();
		String token = config.apiToken().trim();
		if (!token.isEmpty()) {
			api.unlinkAccount(token, () -> {}, failure ->
					log.debug("event=unlink_revoke_failed status={} message=\"{}\"",
							failure.getStatusCode(), failure.getMessage()));
		}
		configManager.unsetConfiguration(GeUncutConfig.GROUP, "apiToken");
		FlipsPanel target = panel;
		SwingUtilities.invokeLater(target::clearAccountData);
		refreshFlips();
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
				offer.getTotalQuantity(),
				offer.getPrice(),
				now).ifPresent(this::onFill);
		offerSync.record(
				event.getSlot(),
				offer.getItemId(),
				offer.getState(),
				offer.getQuantitySold(),
				offer.getTotalQuantity(),
				offer.getPrice(),
				now);
		pushOffers();
	}

	private void pushOffers() {
		List<GeOffer> working = offerSync.current();
		Map<Integer, String> itemNames = new HashMap<>();
		for (GeOffer openOffer : working) {
			itemNames.computeIfAbsent(openOffer.getItemId(), this::itemName);
		}
		SwingUtilities.invokeLater(() -> panel.showOffers(working, itemNames));
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!config.autoFillOffers()) {
			hideOfferLine();
			lastAutofillPrompt = null;
			return;
		}
		Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
		if (title == null || title.isHidden()) {
			hideOfferLine();
			lastAutofillPrompt = null;
			return;
		}
		OfferAutofill.Prompt kind = OfferAutofill.promptKind(title.getText());
		if (kind == null) {
			hideOfferLine();
			lastAutofillPrompt = null;
			return;
		}
		int itemId = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
		if (itemId <= 0) {
			return;
		}
		boolean sell = client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
		String promptKey = itemId + ":" + sell + ":" + kind;
		if (promptKey.equals(lastAutofillPrompt)) {
			return;
		}
		lastAutofillPrompt = promptKey;
		hideOfferLine();
		Long value = OfferAutofill.resolve(itemId, sell, kind, latestFlips, latestPositions,
				Long.toString(client.getAccountHash()));
		if (value == null) {
			return;
		}
		Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		if (container == null) {
			return;
		}
		long amount = value;
		String label = "GE Uncut: set to " + String.format("%,d", amount);
		int labelWidth = label.length() * 8 + 8;
		int containerWidth = container.getWidth() > 0 ? container.getWidth() : 519;
		int[] spot = lineSpot(container, labelWidth, containerWidth);
		int lineX = spot[0];
		int lineY = spot[1];
		Widget line = container.createChild(-1, WidgetType.TEXT);
		line.setText(label);
		line.setFontId(FontID.VERDANA_11_BOLD);
		line.setTextColor(0x000000);
		line.setOriginalX(lineX);
		line.setOriginalY(lineY);
		line.setOriginalWidth(labelWidth);
		line.setOriginalHeight(20);
		line.setXTextAlignment(0);
		line.setHasListener(true);
		line.setAction(1, "Set GE Uncut value");
		line.setOnOpListener((JavaScriptCallback) scriptEvent -> applyOfferValue(amount));
		line.setOnMouseRepeatListener((JavaScriptCallback) scriptEvent -> line.setTextColor(0xFFFFFF));
		line.setOnMouseLeaveListener((JavaScriptCallback) scriptEvent -> line.setTextColor(0x000000));
		line.revalidate();
		offerLine = line;
	}

	private int[] lineSpot(Widget container, int labelWidth, int containerWidth) {
		Widget[] children = container.getDynamicChildren();
		int centerX = Math.max(0, (containerWidth - labelWidth) / 2);
		if (!overlapsAny(children, centerX, 90, labelWidth)) {
			return new int[] { centerX, 90 };
		}
		int rightX = Math.max(0, containerWidth - labelWidth - 8);
		for (int y = 8; y <= 48; y += 20) {
			if (!overlapsAny(children, rightX, y, labelWidth)) {
				return new int[] { rightX, y };
			}
		}
		return new int[] { rightX, 68 };
	}

	private static boolean overlapsAny(Widget[] children, int x, int y, int width) {
		if (children == null) {
			return false;
		}
		for (Widget child : children) {
			if (child == null || child.isHidden()) {
				continue;
			}
			boolean apart = x + width <= child.getOriginalX()
					|| child.getOriginalX() + child.getOriginalWidth() <= x
					|| y + 20 <= child.getOriginalY()
					|| child.getOriginalY() + child.getOriginalHeight() <= y;
			if (!apart) {
				return true;
			}
		}
		return false;
	}

	private void applyOfferValue(long value) {
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		if (input != null) {
			input.setText(value + "*");
		}
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
	}

	private void hideOfferLine() {
		if (offerLine != null) {
			offerLine.setHidden(true);
			offerLine = null;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() != InterfaceID.GE_HISTORY || !linked()) {
			return;
		}
		clientThread.invokeLater(this::syncTradeHistory);
	}

	private void syncTradeHistory() {
		List<GeHistoryRow> rows = GeHistoryParser.parse(client.getWidget(InterfaceID.GeHistory.LIST));
		if (rows.isEmpty()) {
			return;
		}
		api.postGeHistory(Long.toString(client.getAccountHash()), rows,
				() -> log.debug("event=history_synced rows={}", rows.size()),
				failure -> log.debug("event=history_sync_failed status={} message=\"{}\"",
						failure.getStatusCode(), failure.getMessage()));
	}

	private String itemName(int itemId) {
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition != null ? composition.getName() : "Item " + itemId;
	}

	@Provides
	GeUncutConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GeUncutConfig.class);
	}

	private boolean linked() {
		return !config.apiToken().trim().isEmpty();
	}

	private void onFill(OfferDelta delta) {
		log.debug("event=fill_observed item={} side={} quantity={} price_each={} slot={}",
				delta.getItemId(), delta.getSide(), delta.getQuantity(), delta.getPriceEach(), delta.getSlot());
		if (delta.getSide() == OfferDelta.Side.BUY) {
			buyLimits.recordBuy(delta.getItemId(), delta.getQuantity(), delta.getOccurredAt());
		}
		if (linked()) {
			tradeSync.accept(delta);
			executor.schedule(this::refreshPositions, POST_FILL_REFRESH_SECONDS, TimeUnit.SECONDS);
		}
	}

	private void refreshFlips() {
		FlipsPanel target = panel;
		boolean linked = linked();
		SwingUtilities.invokeLater(() -> target.showStatus("Scanning..."));
		refreshPositions();
		refreshMovers();
		flips.fetch(target.selectedScan(), target.selectedRisk(), target.selectedCapital(),
				response -> {
					latestFlips = response.getFlips() != null ? response.getFlips() : List.of();
					SwingUtilities.invokeLater(() -> {
					if (target != panel) {
						return;
					}
					target.applyCapital(linked, response.getMyCapital());
					target.showFlips(response.getFlips(), linked);
					});
				},
				failure -> SwingUtilities.invokeLater(() -> {
					if (target != panel) {
						return;
					}
					if (failure.isUnauthorized()) {
						target.showLinkPrompt();
					} else {
						target.showStatus(failure.getMessage());
					}
				}));
	}

	private void openItem(int itemId) {
		LinkBrowser.browse(GeUncutConfig.API_BASE + "/items/" + itemId);
	}

	private void openMovers() {
		LinkBrowser.browse(GeUncutConfig.API_BASE + "/movers");
	}

	private void openMyFlips() {
		LinkBrowser.browse(GeUncutConfig.API_BASE + "/positions");
	}

	private void refreshPositions() {
		FlipsPanel target = panel;
		positions.fetch(
				response -> {
					latestPositions = response.getPositions() != null ? response.getPositions() : List.of();
					SwingUtilities.invokeLater(() -> {
						if (target == panel) {
							target.showActiveFlips(response);
						}
					});
				},
				failure -> log.debug("event=positions_fetch_failed kind={} status={}",
						failure.getKind(), failure.getStatusCode()));
		refreshHistory();
	}

	private void markNotFlip(long positionId) {
		api.archivePosition(positionId,
				() -> SwingUtilities.invokeLater(() -> {
					refreshPositions();
					refreshHistory();
				}),
				failure -> log.debug("event=archive_position_failed id={} kind={} status={}",
						positionId, failure.getKind(), failure.getStatusCode()));
	}

	private void trackPair(long sellEventId) {
		api.trackPair(sellEventId,
				() -> SwingUtilities.invokeLater(() -> {
					refreshPositions();
					refreshHistory();
				}),
				failure -> log.debug("event=track_pair_failed id={} kind={} status={}",
						sellEventId, failure.getKind(), failure.getStatusCode()));
	}

	private void restoreFlip(long positionId) {
		api.restorePosition(positionId,
				() -> SwingUtilities.invokeLater(() -> {
					refreshPositions();
					refreshHistory();
				}),
				failure -> log.debug("event=restore_position_failed id={} kind={} status={}",
						positionId, failure.getKind(), failure.getStatusCode()));
	}

	private void refreshHistory() {
		FlipsPanel target = panel;
		api.fetchArchived(
				response -> SwingUtilities.invokeLater(() -> {
					if (target == panel) {
						target.showHistory(response);
					}
				}),
				failure -> log.debug("event=archived_fetch_failed kind={} status={}",
						failure.getKind(), failure.getStatusCode()));
	}

	private void refreshMovers() {
		FlipsPanel target = panel;
		api.fetchMovers(
				movers -> SwingUtilities.invokeLater(() -> {
					if (target == panel) {
						target.showMovers(movers);
					}
				}),
				failure -> log.debug("event=movers_fetch_failed kind={} status={}",
						failure.getKind(), failure.getStatusCode()));
	}

	private void linkAccount() {
		if (link.isPairing()) {
			link.openClaimPage();
			return;
		}
		link.begin(
				code -> SwingUtilities.invokeLater(() -> panel.showLinkCode(code)),
				() -> SwingUtilities.invokeLater(this::refreshFlips),
				failure -> SwingUtilities.invokeLater(() -> panel.showStatus(failure.getMessage())));
	}
}
