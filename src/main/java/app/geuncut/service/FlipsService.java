package app.geuncut.service;

import java.util.function.Consumer;
import java.util.List;

import app.geuncut.dto.Flip;

/**
 * The caller's personalized flip list.
 */
public interface FlipsService {
	boolean isLinked();

	void fetch(String scanType, Consumer<List<Flip>> onSuccess, Consumer<String> onError);
}
