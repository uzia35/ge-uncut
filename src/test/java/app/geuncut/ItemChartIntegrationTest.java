package app.geuncut;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import app.geuncut.api.GeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.ItemHistory;
import app.geuncut.dto.ItemPrice;
import app.geuncut.service.ItemHistoryService;
import app.geuncut.service.ItemHistoryService.Snapshot;
import app.geuncut.service.ItemHistoryService.Status;
import app.geuncut.ui.OfferPriceOverlay;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ItemChartIntegrationTest {
	private final Client client = mock(Client.class);
	private final Widget window = mock(Widget.class);
	private final Widget setup = mock(Widget.class);
	private final GeUncutApi api = mock(GeUncutApi.class);
	private final GeUncutConfig config = mock(GeUncutConfig.class);
	private final ItemManager itemManager = mock(ItemManager.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final List<Consumer<ItemHistory>> replies = new ArrayList<>();
	private final ItemHistory empty = new ItemHistory(Collections.emptyList(), Collections.emptyList());
	private GeUncutPlugin plugin;
	private OfferPriceOverlay overlay;

	@Before
	public void setup() throws Exception {
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getWidget(InterfaceID.GeOffers.UNIVERSE)).thenReturn(window);
		when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(setup);
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(536);
		when(config.autoFillOffers()).thenReturn(true);
		when(config.showItemChart()).thenReturn(true);
		doAnswer(call -> { replies.add(call.getArgument(1)); return null; })
				.when(api).fetchItemHistory(anyInt(), any(), any());
		plugin = new GeUncutPlugin();
		put(plugin, "client", client);
		put(plugin, "config", config);
		put(plugin, "api", api);
		put(plugin, "itemManager", itemManager);
		put(plugin, "clientThread", clientThread);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
				.when(clientThread).invoke(any(Runnable.class));
		put(plugin, "itemHistory", new ItemHistoryService(api));
		overlay = (OfferPriceOverlay) get(plugin, "offerOverlay");
	}

	@Test
	public void selectionAndClientFramesSupplyLogicalViewportBounds() {
		when(client.getViewportXOffset()).thenReturn(4);
		when(client.getViewportYOffset()).thenReturn(4);
		when(client.getViewportWidth()).thenReturn(512);
		when(client.getViewportHeight()).thenReturn(334);
		select();
		assertEquals(new java.awt.Rectangle(4, 4, 512, 334), overlay.getParentBounds());
		when(client.getViewportWidth()).thenReturn(900);
		when(client.getViewportHeight()).thenReturn(650);
		frame();
		assertEquals(new java.awt.Rectangle(4, 4, 900, 650), overlay.getParentBounds());
	}

	@Test
	public void selectionDisplaysChartWithoutAPricePromptEvenWithTipsDisabled() throws Exception {
		when(config.autoFillOffers()).thenReturn(false);
		select();
		assertEquals(536, snapshot().getItemId());
		assertEquals(Status.LOADING, snapshot().getStatus());
		replies.get(0).accept(empty);
		frame();
		plugin.onGameTick(new GameTick());
		assertEquals(Status.READY, snapshot().getStatus());
		assertEquals(1, replies.size());
		assertTrue(((List<?>) get(plugin, "offerLines")).isEmpty());
	}

	@Test
	public void repeatedSelectionAndFramesDeduplicateAndRejectABALateResponses() throws Exception {
		select();
		select();
		frame();
		plugin.onGameTick(new GameTick());
		assertEquals(1, replies.size());
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
		select();
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(536);
		select();
		replies.get(0).accept(empty);
		replies.get(1).accept(empty);
		frame();
		assertEquals(536, snapshot().getItemId());
		assertEquals(Status.LOADING, snapshot().getStatus());
		replies.get(2).accept(empty);
		frame();
		assertEquals(Status.READY, snapshot().getStatus());
		assertEquals(3, replies.size());
	}

	@Test
	public void itemSpritesFollowSelectionAndIgnoreOldImageCompletion() throws Exception {
		AsyncBufferedImage bones = new AsyncBufferedImage(mock(ClientThread.class), 36, 32, BufferedImage.TYPE_INT_ARGB);
		AsyncBufferedImage whip = new AsyncBufferedImage(mock(ClientThread.class), 36, 32, BufferedImage.TYPE_INT_ARGB);
		when(itemManager.getImage(536)).thenReturn(bones);
		when(itemManager.getImage(4151)).thenReturn(whip);
		select();
		select();
		frame();
		assertSame(bones, get(overlay, "icon"));
		verify(itemManager, times(1)).getImage(536);
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
		select();
		bones.setRGB(0, 0, 0xffffffff);
		bones.loaded();
		frame();
		assertSame(whip, get(overlay, "icon"));
		whip.setRGB(0, 0, 0xff00ff00);
		whip.loaded();
		assertEquals(0xff00ff00, ((BufferedImage) get(overlay, "icon")).getRGB(0, 0));
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(-1);
		frame();
		assertClosed();
		assertNull(get(overlay, "icon"));
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(536);
		select();
		assertSame(bones, get(overlay, "icon"));
		verify(itemManager, times(2)).getImage(536);
	}

	@Test
	public void deselectAndReselectSameItemInvalidatesOldRequest() throws Exception {
		select();
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(-1);
		frame();
		assertClosed();
		replies.get(0).accept(empty);
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(536);
		select();
		assertEquals(2, replies.size());
		assertEquals(Status.LOADING, snapshot().getStatus());
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(0);
		frame();
		assertClosed();
	}

	@Test
	public void backToOverviewHidesChartEvenWhenSelectedVarpIsRetained() throws Exception {
		select();
		when(setup.isHidden()).thenReturn(true);
		frame();
		replies.get(0).accept(empty);
		assertClosed();
		when(setup.isHidden()).thenReturn(false);
		select();
		assertEquals(Status.LOADING, snapshot().getStatus());
		assertEquals(2, replies.size());
	}

	@Test
	public void closingGeWindowClearsImmediatelyAndCannotReappearFromLateResponse() throws Exception {
		select();
		when(client.getWidget(InterfaceID.GeOffers.UNIVERSE)).thenReturn(null);
		plugin.onWidgetClosed(new WidgetClosed(InterfaceID.GE_OFFERS, 0, true));
		assertClosed();
		replies.get(0).accept(empty);
		frame();
		assertClosed();
		assertEquals(1, replies.size());
	}

	@Test
	public void hiddenWindowOrLoggedOutClientCannotKeepItemDetailsVisible() throws Exception {
		select();
		when(window.isHidden()).thenReturn(true);
		frame();
		assertClosed();
		when(window.isHidden()).thenReturn(false);
		select();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		frame();
		assertClosed();
	}

	@Test
	public void searchListDoesNotDisplayThePreviousSelectionOrCreatePerRowCharts() throws Exception {
		select();
		Widget searchResults = mock(Widget.class);
		when(client.getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS)).thenReturn(searchResults);
		plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GE_ITEM_SEARCH));
		frame();
		assertClosed();
		assertEquals(1, replies.size());
		when(searchResults.isHidden()).thenReturn(true);
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
		select();
		assertEquals(4151, snapshot().getItemId());
		assertEquals(2, replies.size());
	}

	@Test
	public void closingPricePromptKeepsChartButRemovesClickablePriceTips() throws Exception {
		select();
		Widget title = pricePrompt();
		cachePrice();
		Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		when(container.createChild(anyInt(), anyInt())).thenAnswer(call -> mock(Widget.class));
		plugin.onGameTick(new GameTick());
		List<?> lines = (List<?>) get(plugin, "offerLines");
		assertFalse(lines.isEmpty());
		Widget tip = (Widget) lines.get(0);
		when(title.isHidden()).thenReturn(true);
		frame();
		verify(tip).setHidden(true);
		assertTrue(lines.isEmpty());
		assertEquals(536, snapshot().getItemId());
		assertEquals(1, replies.size());
	}

	@Test
	public void detailsSettingOffHidesEntirePopupButKeepsIndependentPriceTips() throws Exception {
		when(config.showItemChart()).thenReturn(false);
		select();
		assertClosed();
		Widget title = pricePrompt();
		cachePrice();
		Widget container = client.getWidget(ComponentID.CHATBOX_CONTAINER);
		when(container.createChild(anyInt(), anyInt())).thenAnswer(call -> mock(Widget.class));
		frame();
		plugin.onGameTick(new GameTick());
		assertClosed();
		assertFalse(((List<?>) get(plugin, "offerLines")).isEmpty());
		assertTrue(replies.isEmpty());
		when(title.isHidden()).thenReturn(true);
		frame();
		assertClosed();
	}

	@Test
	public void disablingDetailsClearsOnConfigEventAndRejectsLateLoadsAcrossReenable() throws Exception {
		select();
		java.awt.Graphics2D graphics = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { overlay.render(graphics); } finally { graphics.dispose(); }
		java.awt.Rectangle button = (java.awt.Rectangle) get(overlay, "expandBounds");
		overlay.handleChartClick(new java.awt.Point(button.x + 2, button.y + 2), id -> fail(), ignoredWebsite -> fail("Unexpected website action"));
		assertEquals(true, get(overlay, "expanded"));
		when(config.showItemChart()).thenReturn(false);
		plugin.onConfigChanged(detailsSettingChanged());
		assertClosed(); // No client frame or game tick needed after the configuration action.
		assertEquals(false, get(overlay, "expanded"));
		assertNull(get(overlay, "expandBounds"));
		assertNull(get(plugin, "overlayIcon"));
		replies.get(0).accept(empty);
		frame();
		select();
		assertClosed();
		assertEquals(1, replies.size());
		when(config.showItemChart()).thenReturn(true);
		plugin.onConfigChanged(detailsSettingChanged());
		assertEquals(Status.LOADING, snapshot().getStatus());
		assertEquals(false, get(overlay, "expanded"));
		assertEquals(2, replies.size());
		replies.get(0).accept(empty);
		frame();
		assertEquals(Status.LOADING, snapshot().getStatus());
		replies.get(1).accept(empty);
		frame();
		assertEquals(Status.READY, snapshot().getStatus());
		verify(clientThread, times(2)).invoke(any(Runnable.class));
	}

	@Test
	public void detailsSettingKeepsSavedKeyAndEnabledDefault() throws Exception {
		ConfigItem setting = GeUncutConfig.class.getMethod("showItemChart").getAnnotation(ConfigItem.class);
		assertEquals("showItemChart", setting.keyName());
		assertEquals("Show GE item details", setting.name());
		assertTrue(new GeUncutConfig() {}.showItemChart());
	}

	@Test
	public void closingPopupSurvivesRefreshAndLateResponseButNewSelectionReopens() throws Exception {
		select();
		closePopup();
		assertTrue(overlay.isDismissed(536));
		replies.get(0).accept(empty); // A completion racing the close cannot paint the popup again.
		frame();
		select();
		plugin.onGameTick(new GameTick());
		assertTrue(overlay.isDismissed(536));
		assertEquals(1, replies.size());
		assertNull(snapshot());
		when(client.getVarpValue(VarPlayer.CURRENT_GE_ITEM)).thenReturn(4151);
		select();
		assertFalse(overlay.isDismissed(4151));
		assertEquals(false, get(overlay, "expanded"));
		assertEquals(4151, snapshot().getItemId());
		assertEquals(2, replies.size());
		closePopup();
		when(setup.isHidden()).thenReturn(true);
		frame();
		assertClosed();
		when(setup.isHidden()).thenReturn(false);
		select();
		assertFalse(overlay.isDismissed(4151));
		assertEquals(false, get(overlay, "expanded"));
		assertEquals(3, replies.size());
	}

	@Test
	public void disableAndReenableResetsManualDismissalAndLateHistory() throws Exception {
		select();
		closePopup();
		when(config.showItemChart()).thenReturn(false);
		plugin.onConfigChanged(detailsSettingChanged());
		assertClosed();
		when(config.showItemChart()).thenReturn(true);
		plugin.onConfigChanged(detailsSettingChanged());
		assertFalse(overlay.isDismissed(536));
		assertEquals(false, get(overlay, "expanded"));
		replies.get(0).accept(empty);
		frame();
		assertEquals(Status.LOADING, snapshot().getStatus());
		replies.get(1).accept(empty);
		frame();
		assertEquals(Status.READY, snapshot().getStatus());
	}

	private void closePopup() throws Exception {
		java.awt.Graphics2D graphics = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try { assertNotNull(overlay.render(graphics)); } finally { graphics.dispose(); }
		java.awt.Rectangle button = (java.awt.Rectangle) get(overlay, "closeBounds");
		assertTrue(overlay.handleChartClick(new java.awt.Point(button.x + 2, button.y + 2), id -> fail(), ignoredWebsite -> fail("Unexpected website action")));
	}

	private ConfigChanged detailsSettingChanged() {
		ConfigChanged event = mock(ConfigChanged.class);
		when(event.getGroup()).thenReturn(GeUncutConfig.GROUP);
		when(event.getKey()).thenReturn("showItemChart");
		return event;
	}

	private void select() { plugin.onScriptPostFired(new ScriptPostFired(ScriptID.GE_OFFERS_SETUP_BUILD)); }
	private void frame() { plugin.onPostClientTick(new PostClientTick()); }
	private Snapshot snapshot() throws Exception { return (Snapshot) get(overlay, "history"); }
	private void assertClosed() throws Exception {
		assertNull(snapshot());
		assertEquals(-1, get(overlay, "itemId"));
	}

	private Widget pricePrompt() {
		Widget title = mock(Widget.class);
		when(title.getText()).thenReturn("Set a price for each item:");
		when(client.getWidget(ComponentID.CHATBOX_TITLE)).thenReturn(title);
		when(client.getWidget(ComponentID.CHATBOX_CONTAINER)).thenReturn(mock(Widget.class));
		return title;
	}

	@SuppressWarnings("unchecked")
	private void cachePrice() throws Exception {
		((Map<Integer, ItemPrice>) get(plugin, "priceCache")).put(536, new ItemPrice(536, 3000L, 3100L, null, null));
	}

	private static void put(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object get(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
