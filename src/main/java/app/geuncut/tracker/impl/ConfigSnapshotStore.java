package app.geuncut.tracker.impl;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.config.GeUncutConfig;
import app.geuncut.model.OfferSnapshot;
import app.geuncut.tracker.SnapshotStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.client.config.ConfigManager;

@Singleton
public class ConfigSnapshotStore implements SnapshotStore {
	private static final String KEY = "offerBaselines";
	private static final Type STORE_TYPE =
			new TypeToken<Map<String, Map<Integer, OfferSnapshot>>>() {}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	public ConfigSnapshotStore(ConfigManager configManager, Gson gson) {
		this.configManager = configManager;
		this.gson = gson;
	}

	@Override
	public Map<Integer, OfferSnapshot> load(String accountHash) {
		if (accountHash == null) {
			return new HashMap<>();
		}
		Map<Integer, OfferSnapshot> slots = readAll().get(accountHash);
		return slots != null ? new HashMap<>(slots) : new HashMap<>();
	}

	@Override
	public void save(String accountHash, Map<Integer, OfferSnapshot> slots) {
		if (accountHash == null) {
			return;
		}
		Map<String, Map<Integer, OfferSnapshot>> all = readAll();
		if (slots.isEmpty()) {
			all.remove(accountHash);
		} else {
			all.put(accountHash, new HashMap<>(slots));
		}
		if (all.isEmpty()) {
			configManager.unsetConfiguration(GeUncutConfig.GROUP, KEY);
		} else {
			configManager.setConfiguration(GeUncutConfig.GROUP, KEY, gson.toJson(all));
		}
	}

	private Map<String, Map<Integer, OfferSnapshot>> readAll() {
		String raw = configManager.getConfiguration(GeUncutConfig.GROUP, KEY);
		if (raw == null || raw.isEmpty()) {
			return new HashMap<>();
		}
		try {
			Map<String, Map<Integer, OfferSnapshot>> parsed = gson.fromJson(raw, STORE_TYPE);
			return parsed != null ? parsed : new HashMap<>();
		} catch (RuntimeException malformed) {
			return new HashMap<>();
		}
	}
}
