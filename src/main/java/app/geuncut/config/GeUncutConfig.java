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
}
