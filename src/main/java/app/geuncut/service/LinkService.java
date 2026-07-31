package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;

public interface LinkService {
	boolean isPairing();

	void openClaimPage();

	void begin(Consumer<String> onCode, Runnable onLinked, Consumer<ApiFailure> onError);

	void cancel();
}
