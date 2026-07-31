package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.FlipsResponse;

public interface FlipsService {
	void fetch(String scanType, String risk, Long capital, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);
}
