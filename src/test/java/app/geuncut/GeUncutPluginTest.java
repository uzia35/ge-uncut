package app.geuncut;

import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.RuneLite;

public class GeUncutPluginTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(GeUncutPlugin.class);
		RuneLite.main(args);
	}
}
