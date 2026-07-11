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

/**
 * Loads the crisp wiki "detail" render for an item (the same image the website
 * shows), falling back to the in-game inventory sprite until it arrives or if
 * the fetch fails. Cached by name so a given item is only fetched once.
 *
 * Uses RuneLite's shared client, which carries no plugin auth, so the token is
 * never sent to the wiki.
 */
public class ItemIconLoader {
	private static final int MAX_CACHE = 512;

	private final OkHttpClient http;
	private final ItemManager itemManager;
	// Keyed by itemId, not name: noted/unnoted variants share a name but not an id.
	// Bounded LRU so a long session browsing many items cannot grow it without limit.
	private final Map<Integer, ImageIcon> cache = Collections.synchronizedMap(
			new LinkedHashMap<Integer, ImageIcon>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Integer, ImageIcon> eldest) {
					return size() > MAX_CACHE;
				}
			});

	public ItemIconLoader(OkHttpClient http, ItemManager itemManager) {
		this.http = http;
		this.itemManager = itemManager;
	}

	// The in-game inventory sprite for an item, no network. RuneLite decodes and
	// caches it; the returned image may still be blank, so onLoaded fires (on the
	// EDT) once it arrives so the caller can repaint.
	Image sprite(int itemId, Runnable onLoaded) {
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(onLoaded));
		return image;
	}

	void load(int itemId, String name, JLabel label, int size) {
		// Inventory sprite first, so the row is never blank while the render loads.
		itemManager.getImage(itemId).addTo(label);
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		ImageIcon cached = cache.get(itemId);
		if (cached != null) {
			label.setIcon(cached);
			return;
		}
		http.newCall(new Request.Builder().url(detailUrl(name)).build()).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
				// Keep the sprite already on the label.
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
					cache.put(itemId, icon);
					SwingUtilities.invokeLater(() -> label.setIcon(icon));
				} catch (IOException unreadable) {
					// Keep the sprite.
				}
			}
		});
	}

	// Scale to fit a size x size box while keeping the render's aspect ratio.
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
