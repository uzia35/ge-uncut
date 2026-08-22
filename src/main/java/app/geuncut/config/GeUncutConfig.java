package app.geuncut.config;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

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

	@ConfigItem(keyName = "autoFillOffers", name = "Show GE price tips", description = "Shows the item's live buy and sell price in the GE offer prompt, with how recently each last traded - click a price to enter it", position = 3)
	default boolean autoFillOffers() {
		return true;
	}

	@Range(min = 0, max = 50)
	@ConfigItem(keyName = "offerAdjustPercent", name = "Offer adjustment", description = "Also offer this far above and below our price in the GE prompts - 0 shows our price only", position = 4)
	default int offerAdjustPercent() {
		return 2;
	}

	@ConfigItem(keyName = "minProfit", name = "Minimum profit", description = "Set from the Finder panel", hidden = true, position = 5)
	default long minProfit() {
		return -1;
	}

	@ConfigItem(keyName = "minRoi", name = "Minimum margin", description = "Set from the Finder panel", hidden = true, position = 6)
	default double minRoi() {
		return -1;
	}

	@ConfigItem(keyName = "installId", name = "Install id", description = "Set automatically on first run", hidden = true, position = 7)
	default String installId() {
		return "";
	}
}
