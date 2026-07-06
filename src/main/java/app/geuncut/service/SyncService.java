package app.geuncut.service;

import java.util.function.Supplier;

/**
 * Lifecycle shared by the timer-driven services that batch state to
 * geuncut.app: start once with a way to read the current account hash, and stop
 * on shutdown.
 */
public interface SyncService {
	void start(Supplier<String> accountHashSupplier);

	void stop();
}
