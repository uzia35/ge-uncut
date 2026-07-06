package app.geuncut.service.impl;

import java.util.function.Consumer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.Flip;
import app.geuncut.service.FlipsService;

/**
 * Read side of the panel: the caller's personalized flip list.
 */
@Singleton
public class FlipsServiceImpl implements FlipsService {
	private final GeUncutApi api;

	@Inject
	public FlipsServiceImpl(GeUncutApi api) {
		this.api = api;
	}

	@Override
	public void fetch(String scanType, Consumer<List<Flip>> onSuccess, Consumer<ApiFailure> onError) {
		api.fetchFlips(scanType, response -> onSuccess.accept(response.getFlips()), onError);
	}
}
