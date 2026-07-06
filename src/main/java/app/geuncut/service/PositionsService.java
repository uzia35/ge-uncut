package app.geuncut.service;

import java.util.function.Consumer;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.PositionsResponse;

/**
 * The caller's open flips with live P&L and portfolio totals. Unlinked surfaces
 * as an unauthorized ApiFailure, same as the flip list.
 */
public interface PositionsService {
	void fetch(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError);
}
