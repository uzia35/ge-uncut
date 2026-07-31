package app.geuncut.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ItemIconLoader {
	private static final int MAX_CACHE = 512;

	private final OkHttpClient http;
	private final ItemManager itemManager;
	private final Map<String, ImageIcon> cache = Collections.synchronizedMap(
			new LinkedHashMap<String, ImageIcon>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
					return size() > MAX_CACHE;
				}
			});

	public ItemIconLoader(OkHttpClient http, ItemManager itemManager) {
		this.http = http;
		this.itemManager = itemManager;
	}

	Image sprite(int itemId, Runnable onLoaded) {
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(onLoaded));
		return image;
	}

	void load(int itemId, String name, JLabel label, int size) {
		AsyncBufferedImage sprite = itemManager.getImage(itemId);
		sprite.onLoaded(() -> SwingUtilities.invokeLater(() ->
				label.setIcon(new ImageIcon(fit(sprite, size)))));
		label.setIcon(new ImageIcon(fit(sprite, size)));
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		String key = itemId + ":" + size;
		ImageIcon cached = cache.get(key);
		if (cached != null) {
			label.setIcon(cached);
			return;
		}
		http.newCall(new Request.Builder().url(detailUrl(name)).build()).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (Response managed = response) {
					if (!managed.isSuccessful() || managed.body() == null) {
						return;
					}
					BufferedImage image = ImageIO.read(managed.body().byteStream());
					if (image == null) {
						return;
					}
					ImageIcon icon = new ImageIcon(fit(image, size));
					cache.put(key, icon);
					SwingUtilities.invokeLater(() -> label.setIcon(icon));
				} catch (IOException unreadable) {
				}
			}
		});
	}

	private static Image fit(BufferedImage image, int size) {
		int width = image.getWidth();
		int height = image.getHeight();
		double scale = (double) size / Math.max(width, height);
		int scaledWidth = Math.max(1, (int) Math.round(width * scale));
		int scaledHeight = Math.max(1, (int) Math.round(height * scale));
		return image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
	}

	private static String detailUrl(String name) {
		String file = name.trim().replace(' ', '_') + "_detail.png";
		return "https://oldschool.runescape.wiki/images/thumb/" + file + "/96px-" + file;
	}
}
