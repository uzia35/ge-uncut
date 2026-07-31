package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.PositionsResponse;

public interface PositionsService {
	void fetch(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);
}
