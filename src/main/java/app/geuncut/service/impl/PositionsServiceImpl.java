package app.geuncut.service.impl;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.PositionsResponse;
import app.geuncut.service.PositionsService;

@Singleton
public class PositionsServiceImpl implements PositionsService {
	private final GeUncutApi api;

	@Inject
	public PositionsServiceImpl(GeUncutApi api) {
		this.api = api;
	}

	@Override
	public void fetch(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError) {
		api.fetchPositions(onSuccess, onError);
	}
}
