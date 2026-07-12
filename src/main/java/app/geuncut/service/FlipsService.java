package app.geuncut.service;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.Flip;

/**
 * The caller's personalized flip list. An unlinked plugin surfaces as an
 * unauthorized ApiFailure; there is no client-side link state to consult.
 */
public interface FlipsService {
	void fetch(String scanType, String risk, Long capital, Consumer<List<Flip>> onSuccess, Consumer<ApiFailure> onError);
}
