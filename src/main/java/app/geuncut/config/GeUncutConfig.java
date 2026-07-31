package app.geuncut.config;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GeUncutConfig.GROUP)
public interface GeUncutConfig extends Config {
	String GROUP = "geuncut";

	// A constant, not a config method: a non-@ConfigItem method on a Config proxy
	// resolves to null and logs a warning, so the base URL lives here instead.
	String API_BASE = "https://geuncut.app";

	// Written by the panel's link flow, read by the transport. Hidden so it never
	// appears in the config panel or is edited by hand; the panel is where you link,
	// unlink, and see account status. It is the only stored value the plugin keeps.
	@ConfigItem(keyName = "apiToken", name = "API token", description = "Set automatically when you link your account", secret = true, hidden = true, position = 1)
	default String apiToken() {
		return "";
	}

	@ConfigItem(keyName = "lightMode", name = "Light mode", description = "Use a light panel theme instead of the default dark", position = 2)
	default boolean lightMode() {
		return false;
	}

	@ConfigItem(keyName = "autoFillOffers", name = "Auto-fill GE offers", description = "Pre-types the price and quantity when you set up a buy on a suggested flip or a sell on a tracked one - just press Enter", position = 3)
	default boolean autoFillOffers() {
		return true;
	}
}
