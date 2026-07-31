package app.geuncut.config;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GeUncutConfig.GROUP)
public interface GeUncutConfig extends Config {
	String GROUP = "geuncut";

	String API_BASE = "https://geuncut.app";

	@ConfigItem(keyName = "apiToken", name = "API token", description = "Set automatically when you link your account", secret = true, hidden = true, position = 1)
	default String apiToken() {
		return "";
	}

	@ConfigItem(keyName = "lightMode", name = "Light mode", description = "Use a light panel theme instead of the default dark", position = 2)
	default boolean lightMode() {
		return false;
	}

	@ConfigItem(keyName = "autoFillOffers", name = "GE offer helper", description = "Adds a clickable GE Uncut line to the price and quantity prompts for suggested and tracked flips - click it to enter the value", position = 3)
	default boolean autoFillOffers() {
		return true;
	}
}
