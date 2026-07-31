package app.geuncut.service;

import java.util.function.Supplier;

public interface SyncService {
	void start(Supplier<String> accountHashSupplier);

	void stop();
}
