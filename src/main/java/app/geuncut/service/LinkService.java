package app.geuncut.service;

import java.util.function.Consumer;

/**
 * Device-link pairing with geuncut.app.
 */
public interface LinkService {
	boolean isPairing();

	void openClaimPage();

	void begin(Consumer<String> onCode, Runnable onLinked, Consumer<String> onError);

	void cancel();
}
