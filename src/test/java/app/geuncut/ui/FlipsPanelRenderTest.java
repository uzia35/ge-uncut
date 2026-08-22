package app.geuncut.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JLabel;

import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.PositionsResponse;
import app.geuncut.dto.ScanRequest;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlipsPanelRenderTest {
	private static final int PANEL_WIDTH = net.runelite.client.ui.PluginPanel.PANEL_WIDTH;
	private static final LongConsumer noopArchive = id -> {
	};

	private static final String ACTIVE_FLIPS_JSON = "{\"summary\":{\"total_realized\":0,\"today_realized\":0," +
			"\"open_unrealized\":506000,\"open_count\":2,\"flips\":0,\"win_rate\":null,\"avg_roi\":null}," +
			"\"positions\":[{\"id\":42,\"item_id\":22124,\"item_name\":\"Superior dragon bones\",\"quantity\":1097," +
			"\"buy_price\":20065,\"sold_qty\":400,\"sold_avg_price\":21010,\"realized_profit\":210000," +
			"\"unrealized_profit\":506000,\"roi\":2.3,\"phase\":\"sell\",\"sell_reason\":\"target_hit\",\"price_stale\":false," +
			"\"account_hash\":\"acct-a\",\"target_sell_price\":21500,\"alert_price\":21200," +
			"\"horizon_days\":3,\"opened_at\":\"2026-07-17T12:00:00\"}," +
			"{\"id\":43,\"item_id\":2,\"item_name\":\"Cannonball\",\"quantity\":5000,\"buy_price\":180," +
			"\"unrealized_profit\":null,\"roi\":null,\"phase\":\"exit\",\"price_stale\":true," +
			"\"account_hash\":\"acct-b\"}]}";

	private static final String ARCHIVED_JSON = "{\"positions\":[" +
			"{\"id\":7,\"item_id\":231,\"item_name\":\"Snape grass\",\"quantity\":600,\"buy_price\":480,\"opened_at\":\"2026-07-10T12:00:00\"}," +
			"{\"id\":9,\"item_id\":1637,\"item_name\":\"Sapphire ring\",\"quantity\":10,\"buy_price\":900," +
			"\"exit_price\":1100,\"realized_profit\":1780,\"tax_paid\":220,\"opened_at\":\"2026-07-15T09:00:00\",\"closed_at\":\"2026-07-18T10:00:00\"}]," +
			"\"completed\":[{\"id\":11,\"item_id\":22124,\"item_name\":\"Superior dragon bones\",\"quantity\":3200,\"buy_price\":19825," +
			"\"sold_qty\":3200,\"sold_avg_price\":20500,\"realized_profit\":848000,\"tax_paid\":1312000," +
			"\"opened_at\":\"2026-07-20T05:00:00\",\"closed_at\":\"2026-07-20T13:43:00\"}]," +
			"\"margin_checks\":[{\"sell_event_id\":501,\"item_id\":1623,\"item_name\":\"Uncut diamond\",\"buy_price\":2633,\"sell_price\":2633,\"profit\":-52,\"tax\":52,\"occurred_at\":\"2026-07-19T08:00:00\"}]," +
			"\"archived_sells\":[{\"item_id\":1601,\"item_name\":\"Diamond\",\"quantity\":8,\"price_each\":1640,\"occurred_at\":\"2026-07-19T09:00:00+00:00\"}]}";

	private static final String MOVERS_JSON = "{\"risers\":[" +
			"{\"item_id\":1,\"name\":\"Hueycoatl hide vambraces\",\"change_pct\":21.8,\"price\":38213,\"volume_day\":124000}," +
			"{\"item_id\":2,\"name\":\"Watermelon seed\",\"change_pct\":31.6,\"price\":52,\"volume_day\":2400000}]," +
			"\"fallers\":[" +
			"{\"item_id\":3,\"name\":\"Ghorrock teleport (tablet)\",\"change_pct\":-40.3,\"price\":11890,\"volume_day\":8100}]," +
			"\"volume_spikes\":[" +
			"{\"item_id\":4,\"name\":\"Wine of zamorak\",\"change_pct\":2.1,\"price\":1210,\"volume_day\":910000,\"volume_ratio\":4.2}]}";

	private static final String FLIPS_JSON = "{\"flips\":[" +
			"{\"item_id\":1623,\"name\":\"Uncut diamond\",\"buy_price\":2512,\"target_sell_price\":2617," +
			"\"quantity\":10000,\"total_profit\":530000,\"profit_per_item\":53,\"position_cost\":25120000," +
			"\"roi\":2.1,\"roi_per_day\":0.7,\"entry_discount_pct\":4.0,\"strategy\":\"value\",\"members\":false," +
			"\"demand_trend\":{\"direction\":\"steady\",\"ratio\":0.8}," +
			"\"buy_fill\":{\"worst_case_hours\":0.23},\"sell_fill\":{\"worst_case_hours\":0.53}}," +
			"{\"item_id\":383,\"name\":\"Marlin\",\"buy_price\":3522,\"target_sell_price\":3927," +
			"\"quantity\":28392,\"total_profit\":9280000,\"profit_per_item\":327,\"position_cost\":100000000," +
			"\"roi\":9.3,\"roi_per_day\":3.1,\"entry_discount_pct\":10.3,\"strategy\":\"standard\",\"members\":true," +
			"\"horizon\":{\"recommended_days\":3}," +
			"\"demand_trend\":{\"direction\":\"rising\",\"ratio\":1.4}," +
			"\"news\":{\"direction\":\"up\",\"confidence\":80}," +
			"\"buy_fill\":{\"worst_case_hours\":108.0},\"sell_fill\":{\"worst_case_hours\":74.4}}" +
			"]}";

	private static ItemIconLoader stubIcons() {
		return new ItemIconLoader(null, null) {
			@Override
			Image sprite(int itemId, Runnable onLoaded) {
				return new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			}

			@Override
			void load(int itemId, String name, javax.swing.JLabel label, int size) {
			}
		};
	}

	@Test
	public void rendersUnlinkedAndLinkedPanels() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showFlips(response.getFlips(), false);
		BufferedImage unlinked = paint(panel);
		write(unlinked, "panel-unlinked.png");

		panel.showFlips(response.getFlips(), true);
		write(paint(panel), "panel-linked.png");

		assertTrue(countDistinctColors(unlinked) > 8);
	}

	@Test
	public void finderBarCarriesTheOfferTipControls() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showFlips(response.getFlips(), true);

		assertNotNull(findLabel(panel, "Show GE price tips"));
		assertNotNull(findLabel(panel,"Offer ± 2%"));
		assertTrue(panel.offerHelperEnabled());
		assertEquals(2, panel.offerAdjustPercent());
		write(paint(panel), "panel-offer-tips.png");

		int[] saves = { 0 };
		panel.setOnOfferSettingsChange(() -> saves[0]++);
		panel.applyOfferSettings(false, 5);
		assertTrue(!panel.offerHelperEnabled());
		assertEquals(5, panel.offerAdjustPercent());
		assertEquals("applying settings must not echo back as a save", 0, saves[0]);
		write(paint(panel), "panel-offer-tips-off.png");

		panel.applyOfferSettings(true, 0);
		assertEquals(0, panel.offerAdjustPercent());
		assertNotNull(findLabel(panel,"No adjustment"));

		panel.applyOfferSettings(true, 13);
		assertEquals("a percentage typed in RuneLite settings must survive, not snap to a preset",
				13, panel.offerAdjustPercent());
		assertNotNull(findLabel(panel,"Offer ± 13%"));

		panel.applyOfferSettings(true, 999);
		assertEquals("out-of-range percentages clamp rather than reaching the offer prompt",
				50, panel.offerAdjustPercent());
		assertNotNull(findLabel(panel,"Offer ± 50%"));
	}

	@Test
	public void finderBarCarriesTheProfitAndMarginBars() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showFlips(response.getFlips(), true);

		// Unset by default: the scan's own bar applies, so nothing is sent and the label
		// says so rather than showing a number the panel invented.
		assertNotNull(findLabel(panel, "Default profit bar"));
		assertNotNull(findLabel(panel, "Default margin bar"));
		assertNull(panel.selectedMinProfit());
		assertNull(panel.selectedMinRoi());

		// Both bars explain themselves through a "?" chip, the way the History actions do.
		// The chip has to be visible and carry a tooltip: an earlier layout squeezed it to
		// zero width, which left the two controls with no explanation at all.
		for (String bar : new String[] { "profit", "margin" }) {
			JLabel chip = helpChipFor(panel, bar);
			assertNotNull("no ? chip beside the " + bar + " bar", chip);
			assertNotNull("the " + bar + " chip has no tooltip", chip.getToolTipText());
			assertTrue("the " + bar + " chip rendered at zero width", chip.getPreferredSize().width > 6);
		}
		write(paint(panel), "panel-scan-bars.png");

		// Unlinked: presets only, and the Default entry picks up the real figure the
		// server reports for whichever scan is running.
		panel.applyScanBars(false, null, null, 1_000_000L, 3.0);
		assertNotNull(findLabel(panel, "Default (1.0M gp)"));
		assertNotNull(findLabel(panel, "Default (3%)"));
		assertNull("an unlinked panel has no saved value to fall back on", panel.selectedMinProfit());

		// Linked: the website's saved bars lead the list and are selected, the same way
		// capital already works. This is the logged-in half of matching the website.
		int[] saves = { 0 };
		panel.setOnScanBarsChange(() -> saves[0]++);
		panel.applyScanBars(true, 750_000L, 1.5, 1_000_000L, 3.0);
		assertNotNull(findLabel(panel, "My profit bar (750k gp)"));
		assertNotNull(findLabel(panel, "My margin bar (1.5%)"));
		assertEquals(Long.valueOf(750_000L), panel.selectedMinProfit());
		assertEquals(Double.valueOf(1.5), panel.selectedMinRoi());
		assertEquals("hydrating from the server must not echo back as a save", 0, saves[0]);
		write(paint(panel), "panel-scan-bars-linked.png");
	}

	@Test
	public void aBarChosenInThePanelSurvivesTheNextScan() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);

		// Restored from RuneLite config on startup, exactly as the plugin does it.
		panel.applySavedScanBars(250_000L, 5.0);
		assertEquals(Long.valueOf(250_000L), panel.selectedMinProfit());
		assertEquals(Double.valueOf(5.0), panel.selectedMinRoi());

		// The regression this guards: every scan response calls applyScanBars, and if a
		// linked user's saved value overwrote the choice they just made in the panel, the
		// picker would snap back a second after they touched it.
		panel.applyScanBars(true, 5_000_000L, 0.5, 1_000_000L, 3.0);
		assertEquals(Long.valueOf(250_000L), panel.selectedMinProfit());
		assertEquals(Double.valueOf(5.0), panel.selectedMinRoi());
	}

	@Test
	public void theScanRequestCarriesEveryControlOnTheBar() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.applySavedScanBars(0L, 0.0);

		ScanRequest request = panel.scanRequest();
		assertEquals(panel.selectedScan(), request.getScanType());
		assertEquals(panel.selectedRisk(), request.getRisk());
		assertEquals(panel.selectedCapital(), request.getCapital());
		// Any profit / Any margin, which must reach the API as 0 rather than as unset.
		assertEquals(Long.valueOf(0L), request.getMinProfit());
		assertEquals(Double.valueOf(0.0), request.getMinRoi());
	}

	@Test
	public void linkPromptSurvivesAccountFilterChanges() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showLinkPrompt();
		paint(panel);
		BufferedImage before = paint(panel);
		write(before, "panel-link-prompt.png");

		panel.renderFlips();
		BufferedImage after = paint(panel);
		assertTrue(pixelsEqual(before, after));
	}

	@Test
	public void rendersEachTab() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse flips = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		PositionsResponse positions = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showFlips(flips.getFlips(), true);
		panel.showActiveFlips(positions);

		java.util.List<app.geuncut.dto.GeOffer> offers = java.util.Arrays.asList(
				app.geuncut.dto.GeOffer.builder().slot(0).itemId(314).side("buy").state("bought")
						.quantityFilled(1000).quantityTotal(1000).priceEach(3).occurredAt("2020-01-01T00:00:00Z").build(),
				app.geuncut.dto.GeOffer.builder().slot(2).itemId(536).side("buy").state("buying")
						.quantityFilled(2550).quantityTotal(7500).priceEach(3100).occurredAt("2020-01-01T00:00:00Z").build());
		java.util.Map<Integer, String> names = new java.util.HashMap<>();
		names.put(314, "Feathers");
		names.put(536, "Dragon bones");
		panel.showOffers(offers, names);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		panel.showMovers(new Gson().fromJson(MOVERS_JSON, app.geuncut.dto.Movers.class));

		panel.toggleFlip(42);
		for (String tab : new String[] { "finder", "flips", "movers", "history" }) {
			panel.selectTab(tab);
			write(paint(panel), "panel-tab-" + tab + ".png");
		}
		panel.selectTab("flips");
		assertNotNull(findLabel(panel, "Not a flip"));
		assertNotNull(findLabel(panel, "Item details ↗"));
		panel.selectTab("history");
		assertNotNull(findLabel(panel, "Track as a flip"));
		assertNotNull(findLabel(panel, "Uncut diamond"));
		assertNotNull(findLabel(panel, "Diamond"));
		assertNotNull(findLabel(panel, "Restore"));
		assertNotNull(findLabel(panel, "1,100"));
		assertNotNull(findLabel(panel, "Flipped"));
		assertNotNull(findLabel(panel, "Regular trade"));
		assertNotNull(findLabel(panel, "Made"));
		assertNotNull(findLabel(panel, "+2k"));
		assertNotNull(findLabel(panel, "−52"));
		assertNotNull(findLabel(panel, "Tax paid"));
		assertNotNull(findLabel(panel, "220"));
		assertNotNull(findLabel(panel, "When"));
		assertNotNull(findLabel(panel, "Qty"));
		assertNotNull(findLabel(panel, "?"));
		panel.selectTab("flips");
		assertNotNull(findLabel(panel, "ACCOUNT 1"));
		assertNotNull(findLabel(panel, "ACCOUNT 2"));
		assertNotNull(findLabel(panel, "EXIT"));
		assertNotNull(findLabel(panel, "Sell target"));
		assertNotNull(findLabel(panel, "Alert at"));
		assertNotNull(findLabel(panel, "Held"));
		panel.selectTab("movers");
		assertNotNull(findLabel(panel, "Volume spikes"));
	}

	@Test
	public void historySortsNewestFirstAcrossCardTypes() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		int done = texts.indexOf("Superior dragon bones");
		int sell = texts.indexOf("Diamond");
		int pair = texts.indexOf("Uncut diamond");
		int flip = texts.indexOf("Sapphire ring");
		int buy = texts.indexOf("Snape grass");
		assertTrue(done >= 0 && sell > done && pair > sell && flip > pair && buy > flip);
	}

	@Test
	public void historySplitsByAccountWhenTwoAreLinked() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showHistory(PositionsResponse.builder()
				.archivedSells(java.util.Arrays.asList(
						app.geuncut.dto.ArchivedSell.builder().accountHash("bbb").itemId(1601)
								.itemName("Bones").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:03:00+00:00").build(),
						app.geuncut.dto.ArchivedSell.builder().accountHash("aaa").itemId(1602)
								.itemName("Feather").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:02:00+00:00").build(),
						// No account tag: must still show, under its own heading.
						app.geuncut.dto.ArchivedSell.builder().itemId(1603)
								.itemName("Rope").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:01:00+00:00").build()))
				.build());

		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		assertTrue("no per-account headings", texts.contains("ACCOUNT 1"));
		assertTrue(texts.contains("ACCOUNT 2"));
		assertTrue("an untagged trade was dropped from the record",
				texts.contains("NO ACCOUNT RECORDED"));

		// Sorted hashes decide the numbering, so 'aaa' leads regardless of the order
		// the rows arrived in, and every row sits under its own heading.
		assertTrue(texts.indexOf("ACCOUNT 1") < texts.indexOf("Feather"));
		assertTrue(texts.indexOf("Feather") < texts.indexOf("ACCOUNT 2"));
		assertTrue(texts.indexOf("ACCOUNT 2") < texts.indexOf("Bones"));
		assertTrue(texts.indexOf("Bones") < texts.indexOf("NO ACCOUNT RECORDED"));
		assertTrue(texts.indexOf("NO ACCOUNT RECORDED") < texts.indexOf("Rope"));

		panel.selectTab("history");
		write(paint(panel), "panel-history-accounts.png");
	}

	@Test
	public void loggingInNarrowsPastTheFoldCorrectly() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		java.util.List<app.geuncut.dto.ArchivedSell> sells = new java.util.ArrayList<>();
		// Account 'aaa' alone overruns the ten-card page, so a naive filter that caps
		// before narrowing would show an empty view for the alt.
		for (int i = 0; i < 12; i++) {
			sells.add(app.geuncut.dto.ArchivedSell.builder().accountHash("aaa").itemId(1700 + i)
					.itemName("Main " + i).quantity(1).priceEach(100L)
					.occurredAt("2026-07-19T10:" + String.format("%02d", 30 + i) + ":00+00:00").build());
		}
		sells.add(app.geuncut.dto.ArchivedSell.builder().accountHash("bbb").itemId(1800)
				.itemName("Alt trade").quantity(1).priceEach(100L)
				.occurredAt("2026-07-19T09:00:00+00:00").build());
		panel.showHistory(PositionsResponse.builder().archivedSells(sells).build());

		java.util.List<String> before = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, before);
		assertTrue("the alt's row should be past the fold to begin with",
				!before.contains("Alt trade"));

		panel.setLoggedInAccount("bbb");
		java.util.List<String> after = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, after);
		assertTrue("logging into the alt did not surface its trade", after.contains("Alt trade"));
		assertTrue("logging into the alt still showed the main's trades", !after.contains("Main 0"));
	}

	private static PositionsResponse twoAccountHistory() {
		return PositionsResponse.builder()
				.archivedSells(java.util.Arrays.asList(
						app.geuncut.dto.ArchivedSell.builder().accountHash("aaa").itemId(1601)
								.itemName("Bones").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:03:00+00:00").build(),
						app.geuncut.dto.ArchivedSell.builder().accountHash("bbb").itemId(1602)
								.itemName("Feather").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:02:00+00:00").build()))
				.build();
	}

	@Test
	public void theViewFollowsTheAccountYouLogInto() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showHistory(twoAccountHistory());
		panel.selectTab("history");

		panel.setLoggedInAccount("bbb");
		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		assertTrue("the logged-in account's trade is missing", texts.contains("Feather"));
		assertTrue("the other account's trade should not show", !texts.contains("Bones"));
		// The badge is the only thing saying whose data this is.
		assertTrue("nothing says which account is shown", texts.contains("ACCOUNT 2"));
		assertTrue(!texts.contains("ACCOUNT 1"));
		write(paint(panel), "panel-follows-login.png");

		// Switching characters swings the whole view with it, badge included.
		panel.setLoggedInAccount("aaa");
		texts.clear();
		collectAllLabelTexts(panel, texts);
		assertTrue(texts.contains("Bones"));
		assertTrue(!texts.contains("Feather"));
		assertTrue(texts.contains("ACCOUNT 1"));
		assertTrue(!texts.contains("ACCOUNT 2"));

		// The badge stays put across every tab, so you always know whose data
		// the panel is on.
		for (String tab : new String[] { "finder", "flips", "movers", "history" }) {
			panel.selectTab(tab);
			texts.clear();
			collectAllLabelTexts(panel, texts);
			assertTrue("the badge disappeared on the " + tab + " tab", texts.contains("ACCOUNT 1"));
		}
	}

	@Test
	public void anUntaggedTradeShowsWhicheverAccountIsLoggedIn() {
		// Rows from before fill tagging belong to somebody; hiding them from every
		// account's view would drop them from the record entirely.
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showHistory(PositionsResponse.builder()
				.archivedSells(java.util.Arrays.asList(
						app.geuncut.dto.ArchivedSell.builder().accountHash("bbb").itemId(1601)
								.itemName("Bones").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:03:00+00:00").build(),
						app.geuncut.dto.ArchivedSell.builder().itemId(1603)
								.itemName("Rope").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:01:00+00:00").build()))
				.build());
		panel.setLoggedInAccount("bbb");
		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		assertTrue(texts.contains("Bones"));
		assertTrue("the untagged trade was dropped from the record", texts.contains("Rope"));
	}

	@Test
	public void historyStaysFlatForASingleAccount() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showHistory(PositionsResponse.builder()
				.archivedSells(java.util.Arrays.asList(
						app.geuncut.dto.ArchivedSell.builder().accountHash("aaa").itemId(1601)
								.itemName("Bones").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:03:00+00:00").build(),
						app.geuncut.dto.ArchivedSell.builder().accountHash("aaa").itemId(1602)
								.itemName("Feather").quantity(1).priceEach(100L)
								.occurredAt("2026-07-19T09:02:00+00:00").build()))
				.build());
		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		assertTrue("one account should not get a heading", !texts.contains("ACCOUNT 1"));
		assertTrue(texts.contains("Bones") && texts.contains("Feather"));
	}

	@Test
	public void historyFoldsPastTenCards() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		java.util.List<app.geuncut.dto.ArchivedSell> sells = new java.util.ArrayList<>();
		for (int i = 0; i < 12; i++) {
			sells.add(app.geuncut.dto.ArchivedSell.builder()
					.itemId(1601 + i).itemName("Item " + i).quantity(1).priceEach(100L)
					.occurredAt("2026-07-19T09:" + String.format("%02d", i) + ":00+00:00").build());
		}
		panel.showHistory(PositionsResponse.builder().archivedSells(sells).build());
		java.util.List<String> texts = new java.util.ArrayList<>();
		collectAllLabelTexts(panel, texts);
		assertEquals(10, texts.stream().filter("Regular trade"::equals).count());
		JLabel more = findLabel(panel, "Show 2 more");
		assertNotNull(more);

		MouseEvent click = new MouseEvent(more, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
		for (MouseListener listener : more.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		texts.clear();
		collectAllLabelTexts(panel, texts);
		assertEquals(12, texts.stream().filter("Regular trade"::equals).count());
	}

	@Test
	public void trackPairClickCallsBackWithSellEventId() {
		long[] captured = { -1 };
		LongConsumer capture = id -> captured[0] = id;
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, capture);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		java.util.List<JLabel> tracks = new java.util.ArrayList<>();
		collectLabels(panel, "Restore", tracks);
		assertTrue(tracks.size() >= 2);
		for (JLabel track : tracks) {
			MouseEvent click = new MouseEvent(track, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
			for (MouseListener listener : track.getMouseListeners()) {
				listener.mouseClicked(click);
			}
		}
		assertEquals(501L, captured[0]);
	}

	private static void collectLabels(Component component, String text, java.util.List<JLabel> found) {
		if (component instanceof JLabel && text.equals(((JLabel) component).getText())) {
			found.add((JLabel) component);
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				collectLabels(child, text, found);
			}
		}
	}

	@Test
	public void flipCardExpandsAndCollapses() {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showActiveFlips(new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class));

		assertTrue(findLabel(panel, "Not a flip") == null);
		panel.toggleFlip(42);
		assertNotNull(findLabel(panel, "Not a flip"));
		assertNotNull(findLabel(panel, "Item details ↗"));
		assertNotNull(findLabel(panel, "Total profit"));
		assertNotNull(findLabel(panel, "Avg profit / ea"));
		assertNotNull(findLabel(panel, "Unrealized profit"));
		assertNotNull(findLabel(panel, "Realized so far"));
		assertNotNull(findLabel(panel, "Avg ROI"));
		panel.toggleFlip(42);
		assertTrue(findLabel(panel, "Not a flip") == null);
	}

	@Test
	public void sparseFlipsPaneDoesNotStretchHero() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showActiveFlips(new Gson().fromJson("{\"summary\":{},\"positions\":[]}", PositionsResponse.class));
		panel.selectTab("flips");
		BufferedImage image = paint(panel);
		write(image, "panel-flips-sparse.png");

		int red = new Color(image.getRGB(image.getWidth() / 2, image.getHeight() - 30)).getRed();
		assertTrue("stats card stretched to fill the pane (red=" + red + ")", red < 0x2E);
	}

	@Test
	public void restoreClickCallsBackWithPositionId() {
		long[] captured = { -1 };
		LongConsumer capture = id -> captured[0] = id;
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, capture, noopArchive);
		panel.showHistory(new Gson().fromJson(ARCHIVED_JSON, PositionsResponse.class));
		JLabel restore = findLabel(panel, "Track as a flip");
		assertNotNull(restore);

		MouseEvent click = new MouseEvent(restore, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
		for (MouseListener listener : restore.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		assertEquals(7L, captured[0]);
	}

	@Test
	public void rendersLightThemePanel() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		FlipsResponse response = new Gson().fromJson(FLIPS_JSON, FlipsResponse.class);
		try {
			FlipsPanel.applyTheme(true);
			FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
			panel.showFlips(response.getFlips(), true);
			BufferedImage light = paint(panel);
			write(light, "panel-light.png");
			assertTrue("light surface should be bright", new Color(light.getRGB(4, 4)).getRed() > 0xC0);
		} finally {
			FlipsPanel.applyTheme(false);
		}
	}

	@Test
	public void activeFlipCardShowsNotFlipAffordance() throws Exception {
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		PositionsResponse response = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, noopArchive, noopArchive, noopArchive);
		panel.showActiveFlips(response);
		panel.toggleFlip(42);
		write(paint(panel), "panel-active-flips.png");

		assertNotNull("expected a 'Not a flip' control on the expanded flip card",
				findLabel(panel, "Not a flip"));
	}

	@Test
	public void notFlipClickCallsBackWithPositionId() {
		long[] captured = { -1 };
		LongConsumer capture = id -> captured[0] = id;
		Runnable noop = () -> {
		};
		IntConsumer noopItem = item -> {
		};
		PositionsResponse response = new Gson().fromJson(ACTIVE_FLIPS_JSON, PositionsResponse.class);

		FlipsPanel panel = new FlipsPanel(noop, noop, noop, noop, noop, stubIcons(), noopItem, capture, noopArchive, noopArchive);
		panel.showActiveFlips(response);
		panel.toggleFlip(42);
		JLabel notFlip = findLabel(panel, "Not a flip");
		assertNotNull(notFlip);

		MouseEvent click = new MouseEvent(notFlip, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false);
		for (MouseListener listener : notFlip.getMouseListeners()) {
			listener.mouseClicked(click);
		}
		assertEquals(42L, captured[0]);
	}

	private static void collectAllLabelTexts(Component component, java.util.List<String> out) {
		if (component instanceof JLabel) {
			out.add(((JLabel) component).getText());
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				collectAllLabelTexts(child, out);
			}
		}
	}

	private static AbstractButton findButton(Component component, String text) {
		if (component instanceof AbstractButton && text.equals(((AbstractButton) component).getText())) {
			return (AbstractButton) component;
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				AbstractButton found = findButton(child, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JLabel findLabel(Component component, String text) {
		if (component instanceof JLabel && text.equals(((JLabel) component).getText())) {
			return (JLabel) component;
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				JLabel found = findLabel(child, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static JLabel helpChipFor(Component component, String word) {
		if (component instanceof JLabel) {
			JLabel label = (JLabel) component;
			String tip = label.getToolTipText();
			if ("?".equals(label.getText()) && tip != null && tip.contains(word)) {
				return label;
			}
		}
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				JLabel found = helpChipFor(child, word);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return false;
		}
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static BufferedImage paint(FlipsPanel panel) {
		panel.setSize(PANEL_WIDTH, Math.max(panel.getPreferredSize().height, 400));
		layoutTree(panel);
		panel.setSize(PANEL_WIDTH, Math.max(panel.getPreferredSize().height, 400));
		layoutTree(panel);
		BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		panel.paint(g2);
		g2.dispose();
		return image;
	}

	private static void layoutTree(Component component) {
		component.doLayout();
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents()) {
				layoutTree(child);
			}
		}
	}

	private static void write(BufferedImage image, String name) throws Exception {
		File out = new File("build/preview/" + name);
		out.getParentFile().mkdirs();
		ImageIO.write(image, "png", out);
	}

	private static int countDistinctColors(BufferedImage image) {
		java.util.Set<Integer> colors = new java.util.HashSet<>();
		for (int y = 0; y < image.getHeight(); y += 7) {
			for (int x = 0; x < image.getWidth(); x += 7) {
				colors.add(image.getRGB(x, y));
			}
		}
		return colors.size();
	}
}
