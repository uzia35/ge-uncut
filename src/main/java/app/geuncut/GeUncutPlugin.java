package app.geuncut;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;

import app.geuncut.api.GeUncutApi;
import app.geuncut.api.impl.HttpGeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.ItemPrice;
import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.OfferAutofill;
import app.geuncut.tracker.PriceHint;
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
import app.geuncut.tracker.FillLog;
import app.geuncut.tracker.impl.FileFillLog;
import app.geuncut.tracker.impl.BuyLimitTrackerImpl;
import app.geuncut.tracker.impl.ConfigSnapshotStore;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import app.geuncut.tracker.OfferTracker;
import app.geuncut.tracker.SnapshotStore;
import app.geuncut.ui.FlipsPanel;
import app.geuncut.ui.ItemIconLoader;
import app.geuncut.ui.OfferPriceOverlay;
import com.google.gson.Gson;
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
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
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
		binder.bind(SnapshotStore.class).to(ConfigSnapshotStore.class);
		binder.bind(BuyLimitTracker.class).to(BuyLimitTrackerImpl.class);
	}

	@Provides
	@Singleton
	FillLog provideFillLog(Gson gson) {
		return new FileFillLog(gson, new File(RuneLite.RUNELITE_DIR, "geuncut"));
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
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

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
	private final OfferPriceOverlay offerOverlay = new OfferPriceOverlay();
	private int overlayIconItemId = -1;
	private BufferedImage overlayIcon;
	private final MouseListener offerMouseListener = new MouseAdapter() {
		@Override
		public MouseEvent mousePressed(MouseEvent event) {
			if (overlayIconItemId > 0 && offerOverlay.isDetailsClick(event.getPoint())) {
				openItem(overlayIconItemId);
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent event) {
			offerOverlay.setDetailsHover(offerOverlay.isDetailsClick(event.getPoint()));
			return event;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent event) {
			offerOverlay.setDetailsHover(offerOverlay.isDetailsClick(event.getPoint()));
			return event;
		}
	};
	private java.util.concurrent.ScheduledFuture<?> positionsPoll;
	private boolean offerReplayPending;
	private static final int OFFER_LINE_COLOR = 0x1565C0;
	private static final int OFFER_LINE_HOVER_COLOR = 0xFFFFFF;
	private static final int OFFER_ROW_MARGIN = 30;
	private static final int OFFER_LINE_GAP = 16;
	private static final int OFFER_TEXT_HEIGHT = 14;
	private static final int OFFER_CLEARANCE = 4;
	private static final int OFFER_EDGE_MARGIN = 8;
	private static final int OFFER_ROW_Y = 90;
	private static final long PRICE_TTL_MS = 60_000;

	private volatile String lastAutofillPrompt;
	private final List<Widget> offerLines = new ArrayList<>();
	private final Map<Integer, ItemPrice> priceCache = new ConcurrentHashMap<>();
	private final Map<Integer, Long> priceFetchedAt = new ConcurrentHashMap<>();
	private final Set<Integer> priceInFlight = ConcurrentHashMap.newKeySet();

	private static final int POSITIONS_POLL_SECONDS = 30;
	private static final int POST_FILL_REFRESH_SECONDS = 8;

	@Override
	protected void startUp() {
		installPanel();
		overlayManager.add(offerOverlay);
		mouseManager.registerMouseListener(offerMouseListener);

		tradeSync.setSendGate(this::linked);
		tradeSync.setInstallId(installId());
		tradeSync.start(this::trackedAccount);
		offerSync.start(() -> linked() ? Long.toString(client.getAccountHash()) : null);
		if (linked()) {
			startPositionsPoll();
		}
		if (client.getGameState() == GameState.LOGGED_IN) {
			long hash = client.getAccountHash();
			offerTracker.loadFor(hash != -1 ? Long.toString(hash) : null);
		}
		refreshFlips();
		seedOfferPlacements();
	}

	private String trackedAccount() {
		long hash = client.getAccountHash();
		return hash != -1 ? Long.toString(hash) : null;
	}

	private String installId() {
		String stored = config.installId().trim();
		if (!stored.isEmpty()) {
			return stored;
		}
		String minted = java.util.UUID.randomUUID().toString();
		configManager.setConfiguration(GeUncutConfig.GROUP, "installId", minted);
		return minted;
	}

	private void startPositionsPoll() {
		if (positionsPoll != null) {
			return;
		}
		positionsPoll = executor.scheduleWithFixedDelay(this::refreshPositions,
				POSITIONS_POLL_SECONDS, POSITIONS_POLL_SECONDS, TimeUnit.SECONDS);
	}

	private void stopPositionsPoll() {
		if (positionsPoll != null) {
			positionsPoll.cancel(false);
			positionsPoll = null;
		}
	}

	private void onTabOpened(String tab) {
		if ("flips".equals(tab)) {
			refreshPositions();
		} else if ("history".equals(tab)) {
			refreshHistory();
		} else if ("movers".equals(tab)) {
			refreshMovers();
		}
	}

	private void seedOfferPlacements() {
		if (!linked()) {
			return;
		}
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
		panel.setOnTabOpen(this::onTabOpened);
		panel.applyOfferSettings(config.autoFillOffers(), config.offerAdjustPercent());
		panel.setOnOfferSettingsChange(this::saveOfferSettings);
		panel.applySavedScanBars(config.minProfit(), config.minRoi());
		panel.setOnScanBarsChange(this::saveScanBars);
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
		if (!GeUncutConfig.GROUP.equals(event.getGroup())) {
			return;
		}
		if ("autoFillOffers".equals(event.getKey()) || "offerAdjustPercent".equals(event.getKey())) {
			FlipsPanel target = panel;
			SwingUtilities.invokeLater(
					() -> target.applyOfferSettings(config.autoFillOffers(), config.offerAdjustPercent()));
			return;
		}
		if (!"lightMode".equals(event.getKey())) {
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
		stopPositionsPoll();
		tradeSync.stop();
		offerSync.stop();
		overlayManager.remove(offerOverlay);
		mouseManager.unregisterMouseListener(offerMouseListener);
		offerOverlay.clear();
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
			long hash = client.getAccountHash();
			String account = hash != -1 ? Long.toString(hash) : null;
			offerTracker.loadFor(account);
			SwingUtilities.invokeLater(() -> {
				panel.setGameActive(true);
				panel.setLoggedInAccount(account);
			});
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
		stopPositionsPoll();
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
				now,
				this::onFill);
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
			clearOffer();
			return;
		}
		Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
		if (title == null || title.isHidden()) {
			clearOffer();
			return;
		}
		OfferAutofill.Prompt kind = OfferAutofill.promptKind(title.getText());
		if (kind != OfferAutofill.Prompt.PRICE) {
			clearOffer();
			return;
		}
		int itemId = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
		if (itemId <= 0) {
			clearOffer();
			return;
		}
		Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		if (container == null) {
			clearOffer();
			return;
		}
		boolean sell = client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
		ItemPrice market = marketPrice(itemId);
		if (market != null && PriceHint.hasPrices(market)) {
			if (overlayIconItemId != itemId) {
				overlayIconItemId = itemId;
				overlayIcon = itemManager.getImage(itemId);
			}
			offerOverlay.show(market, itemName(itemId), overlayIcon);
		} else {
			offerOverlay.clear();
		}
		String promptKey = itemId + ":" + sell + ":" + kind;
		if (promptKey.equals(lastAutofillPrompt)
				&& !offerLines.isEmpty() && offerLinesAttached(container)) {
			return;
		}
		lastAutofillPrompt = null;
		hideOfferLine();
		if (market == null) {
			return;
		}
		long side = PriceHint.sidePrice(market, sell);
		if (side <= 0) {
			return;
		}
		Long value = side;
		int containerWidth = container.getWidth() > 0 ? container.getWidth() : 519;
		int available = containerWidth - OFFER_ROW_MARGIN;
		int percent = config.offerAdjustPercent();
		List<OfferAutofill.Choice> choices = OfferAutofill.choices(value, kind, percent);
		if (choices.isEmpty()) {
			return;
		}
		if (!fits(choices, available)) {
			List<OfferAutofill.Choice> shortened = OfferAutofill.choices(value, kind, percent, true);
			if (fits(shortened, available)) {
				choices = shortened;
			}
		}
		int cell = choiceWidth(choices);
		int[] spot = rowSpot(container, rowSpan(choices, cell), containerWidth);
		int lineX = spot[0];
		for (OfferAutofill.Choice choice : choices) {
			offerLines.add(createOfferLine(container, choice, lineX, spot[1], cell));
			lineX += cell + OFFER_LINE_GAP;
		}
		lastAutofillPrompt = promptKey;
	}

	private void clearOffer() {
		hideOfferLine();
		lastAutofillPrompt = null;
		overlayIconItemId = -1;
		offerOverlay.clear();
	}

	private ItemPrice marketPrice(int itemId) {
		long now = System.currentTimeMillis();
		Long fetchedAt = priceFetchedAt.get(itemId);
		boolean stale = fetchedAt == null || now - fetchedAt >= PRICE_TTL_MS;
		if (stale && priceInFlight.add(itemId)) {
			api.fetchItemPrice(itemId,
					price -> {
						priceInFlight.remove(itemId);
						priceFetchedAt.put(itemId, System.currentTimeMillis());
						if (price != null) {
							priceCache.put(itemId, price);
							lastAutofillPrompt = null;
						}
					},
					failure -> {
						priceInFlight.remove(itemId);
						priceFetchedAt.put(itemId, System.currentTimeMillis());
						log.debug("event=item_price_fetch_failed item={} kind={} status={}",
								itemId, failure.getKind(), failure.getStatusCode());
					});
		}
		return priceCache.get(itemId);
	}

	private static int choiceWidth(List<OfferAutofill.Choice> choices) {
		int widest = 0;
		for (OfferAutofill.Choice choice : choices) {
			widest = Math.max(widest, labelWidth(choice.getLabel()));
		}
		return widest;
	}

	private Widget createOfferLine(Widget container, OfferAutofill.Choice choice,
			int lineX, int lineY, int labelWidth) {
		long amount = choice.getValue();
		Widget line = container.createChild(-1, WidgetType.TEXT);
		line.setText(choice.getLabel());
		line.setFontId(FontID.VERDANA_11_BOLD);
		line.setTextColor(OFFER_LINE_COLOR);
		line.setOriginalX(lineX);
		line.setOriginalY(lineY);
		line.setOriginalWidth(labelWidth);
		line.setOriginalHeight(OFFER_TEXT_HEIGHT);
		line.setXTextAlignment(1);
		line.setHasListener(true);
		line.setAction(1, "Set GE Uncut value");
		line.setOnOpListener((JavaScriptCallback) scriptEvent -> applyOfferValue(amount));
		line.setOnMouseRepeatListener((JavaScriptCallback) scriptEvent -> line.setTextColor(OFFER_LINE_HOVER_COLOR));
		line.setOnMouseLeaveListener((JavaScriptCallback) scriptEvent -> line.setTextColor(OFFER_LINE_COLOR));
		line.revalidate();
		return line;
	}

	private boolean offerLinesAttached(Widget container) {
		Widget[] children = container.getDynamicChildren();
		if (children == null) {
			return false;
		}
		Widget first = offerLines.get(0);
		for (Widget child : children) {
			if (child == first) {
				return true;
			}
		}
		return false;
	}

	private static int labelWidth(String label) {
		return label.length() * 8 + 8;
	}

	private static int rowSpan(List<OfferAutofill.Choice> choices, int cell) {
		return choices.size() * cell + (choices.size() - 1) * OFFER_LINE_GAP;
	}

	static boolean fits(List<OfferAutofill.Choice> choices, int available) {
		return rowSpan(choices, choiceWidth(choices)) <= available;
	}

	static List<OfferAutofill.Choice> fitChoices(List<OfferAutofill.Choice> choices, int available) {
		if (choices.size() < 2 || fits(choices, available)) {
			return choices;
		}
		OfferAutofill.Choice base = null;
		for (OfferAutofill.Choice choice : choices) {
			if (choice.isBase()) {
				base = choice;
			}
		}
		if (base == null) {
			return choices;
		}
		for (OfferAutofill.Choice choice : choices) {
			if (choice != base) {
				List<OfferAutofill.Choice> pair = List.of(base, choice);
				if (fits(pair, available)) {
					return pair;
				}
			}
		}
		return List.of(base);
	}

	private int[] rowSpot(Widget container, int span, int containerWidth) {
		Widget[] children = container.getDynamicChildren();
		int centerX = Math.max(0, (containerWidth - span) / 2);
		if (!overlapsAny(children, centerX, OFFER_ROW_Y, span, OFFER_TEXT_HEIGHT)) {
			return new int[] { centerX, OFFER_ROW_Y };
		}
		int rightX = Math.max(0, containerWidth - span - OFFER_EDGE_MARGIN);
		for (int y = OFFER_ROW_Y; y >= OFFER_EDGE_MARGIN; y -= OFFER_TEXT_HEIGHT + OFFER_CLEARANCE) {
			if (!overlapsAny(children, rightX, y, span, OFFER_TEXT_HEIGHT)) {
				return new int[] { rightX, y };
			}
		}
		return new int[] { centerX, OFFER_ROW_Y };
	}

	private static boolean overlapsAny(Widget[] children, int x, int y, int width, int height) {
		if (children == null) {
			return false;
		}
		for (Widget child : children) {
			if (child == null || child.isHidden()) {
				continue;
			}
			boolean apart = x + width + OFFER_CLEARANCE <= child.getOriginalX()
					|| child.getOriginalX() + child.getOriginalWidth() + OFFER_CLEARANCE <= x
					|| y + height + OFFER_CLEARANCE <= child.getOriginalY()
					|| child.getOriginalY() + child.getOriginalHeight() + OFFER_CLEARANCE <= y;
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
		for (Widget line : offerLines) {
			line.setHidden(true);
		}
		offerLines.clear();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == InterfaceID.GE_OFFERS) {
			refreshFlips();
		}
	}

	private String itemName(int itemId) {
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return composition != null ? composition.getName() : "Item " + itemId;
	}

	@Provides
	GeUncutConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(GeUncutConfig.class);
	}

	private void saveOfferSettings() {
		configManager.setConfiguration(GeUncutConfig.GROUP, "autoFillOffers", panel.offerHelperEnabled());
		configManager.setConfiguration(GeUncutConfig.GROUP, "offerAdjustPercent", panel.offerAdjustPercent());
	}

	private void saveScanBars() {
		Long profit = panel.selectedMinProfit();
		Double roi = panel.selectedMinRoi();
		configManager.setConfiguration(GeUncutConfig.GROUP, "minProfit", profit == null ? -1L : profit);
		configManager.setConfiguration(GeUncutConfig.GROUP, "minRoi", roi == null ? -1.0 : roi);
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
		tradeSync.accept(delta);
		if (linked()) {
			executor.schedule(this::refreshPositions, POST_FILL_REFRESH_SECONDS, TimeUnit.SECONDS);
		}
	}

	private void refreshFlips() {
		FlipsPanel target = panel;
		boolean linked = linked();
		SwingUtilities.invokeLater(() -> target.showStatus("Scanning..."));
		refreshPositions();
		refreshMovers();
		flips.fetch(target.scanRequest(),
				response -> {
					SwingUtilities.invokeLater(() -> {
					if (target != panel) {
						return;
					}
					target.applyCapital(linked, response.getMyCapital());
					target.applyScanBars(linked, response.getMyMinProfit(), response.getMyMinRoi(),
							response.getMinProfitFloor(), response.getMinRoiFloor());
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
		if (!linked()) {
			SwingUtilities.invokeLater(() -> target.clearRefreshStatus("flips"));
			return;
		}
		positions.fetch(
				response -> {
					SwingUtilities.invokeLater(() -> {
						if (target == panel) {
							target.showActiveFlips(response);
						}
					});
				},
				failure -> {
					if (failure.isUnauthorized()) {
						stopPositionsPoll();
					}
					log.debug("event=positions_fetch_failed kind={} status={}",
							failure.getKind(), failure.getStatusCode());
					SwingUtilities.invokeLater(() -> {
						if (target == panel) {
							target.markRefreshFailed("flips");
						}
					});
				});
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
		if (!linked()) {
			SwingUtilities.invokeLater(() -> target.clearRefreshStatus("history"));
			return;
		}
		api.fetchArchived(
				response -> SwingUtilities.invokeLater(() -> {
					if (target == panel) {
						target.showHistory(response);
					}
				}),
				failure -> {
					log.debug("event=archived_fetch_failed kind={} status={}",
							failure.getKind(), failure.getStatusCode());
					SwingUtilities.invokeLater(() -> {
						if (target == panel) {
							target.markRefreshFailed("history");
						}
					});
				});
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
				() -> {
					startPositionsPoll();
					seedOfferPlacements();
					SwingUtilities.invokeLater(this::refreshFlips);
				},
				failure -> SwingUtilities.invokeLater(() -> panel.showStatus(failure.getMessage())));
	}
}
