package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.FlipsResponse;

/**
 * The caller's personalized flip list plus the response extras the panel
 * renders around it (saved bankroll). Never yields a null response or a null
 * flip list. An unlinked plugin surfaces as an unauthorized ApiFailure; there
 * is no client-side link state to consult.
 */
public interface FlipsService {
	void fetch(String scanType, String risk, Long capital, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError);
}
