package app.geuncut.config;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(GeUncutConfig.GROUP)
public interface GeUncutConfig extends Config {
	String GROUP = "geuncut";

	@ConfigSection(name = "Account", description = "Connection to your geuncut.app account", position = 0)
	String accountSection = "account";

	// Hidden: the pairing flow in the panel writes this, and the transport reads it.
	// It is never entered by hand, so it should not clutter the config or expose the
	// token in the UI. Use "Link account" in the panel to set it.
	@ConfigItem(keyName = "apiToken", name = "API token", description = "Set automatically when you link your account", secret = true, hidden = true, position = 1, section = accountSection)
	default String apiToken() {
		return "";
	}

	@ConfigItem(keyName = "syncTrades", name = "Sync my GE trades", description = "Report your own offer fills to geuncut.app so flips track themselves in My Flips", position = 2, section = accountSection)
	default boolean syncTrades() {
		return true;
	}

	// RuneLite config has no button type, so this is an action toggle: ticking it
	// clears the stored token (the plugin resets it to off). See GeUncutPlugin.
	@ConfigItem(keyName = "unlink", name = "Unlink account", description = "Disconnect this device from your geuncut.app account", position = 3, section = accountSection)
	default boolean unlink() {
		return false;
	}

	// Not a config item, so it never appears in the panel and can't be changed:
	// this is a single-owner product with one backend.
	default String apiBase() {
		return "https://geuncut.app";
	}
}
