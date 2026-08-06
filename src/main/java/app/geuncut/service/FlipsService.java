package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.ScanRequest;

public interface FlipsService {
	void fetch(ScanRequest request, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);
}
