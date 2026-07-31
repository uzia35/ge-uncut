package app.geuncut.service.impl;

import java.util.function.Consumer;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.service.FlipsService;

@Singleton
public class FlipsServiceImpl implements FlipsService {
	private final GeUncutApi api;

	@Inject
	public FlipsServiceImpl(GeUncutApi api) {
		this.api = api;
	}

	@Override
	public void fetch(String scanType, String risk, Long capital, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError) {
		api.fetchFlips(scanType, risk, capital,
				response -> onSuccess.accept(normalize(response)),
				onError);
	}

	private static FlipsResponse normalize(FlipsResponse response) {
		if (response == null) {
			return FlipsResponse.builder().flips(Collections.emptyList()).build();
		}
		if (response.getFlips() == null) {
			return FlipsResponse.builder()
					.flips(Collections.emptyList())
					.myCapital(response.getMyCapital())
					.build();
		}
		return response;
	}
}
