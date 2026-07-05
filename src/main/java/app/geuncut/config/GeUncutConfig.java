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

	@ConfigItem(keyName = "apiToken", name = "API token", description = "Personal token from geuncut.app Settings. The plugin never sees your password.", secret = true, position = 1, section = accountSection)
	default String apiToken() {
		return "";
	}

	@ConfigItem(keyName = "syncTrades", name = "Sync my GE trades", description = "Report your own offer fills to geuncut.app so flips track themselves in My Flips", position = 2, section = accountSection)
	default boolean syncTrades() {
		return true;
	}

	@ConfigItem(keyName = "apiBase", name = "API base URL", description = "Only change this if you self-host", position = 3, section = accountSection)
	default String apiBase() {
		return "https://geuncut.app";
	}
}
