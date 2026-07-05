package app.geuncut.api.impl;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.GeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HTTP transport to geuncut.app. Nothing but HTTP lives here; payload shapes
 * are the dto package, business rules are the service layer.
 */
@Slf4j
@Singleton
public class HttpGeUncutApi implements GeUncutApi {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;
	private final GeUncutConfig config;

	@Inject
	public HttpGeUncutApi(OkHttpClient http, Gson gson, GeUncutConfig config) {
		this.http = http;
		this.gson = gson;
		this.config = config;
	}

	@Override
	public boolean hasToken() {
		return !config.apiToken().trim().isEmpty();
	}

	@Override
	public void fetchFlips(String scanType, Consumer<FlipsResponse> onSuccess, Consumer<String> onError) {
		HttpUrl url = HttpUrl.parse(config.apiBase() + "/api/plugin/flips").newBuilder()
				.addQueryParameter("scan_type", scanType)
				.build();
		Request request = authorized(new Request.Builder().url(url).get()).build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, FlipsResponse.class)));
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<String> onError) {
		JsonObject payload = new JsonObject();
		payload.add("events", gson.toJsonTree(events));
		Request request = authorized(new Request.Builder()
				.url(config.apiBase() + "/api/plugin/ge-events")
				.post(RequestBody.create(JSON, payload.toString())))
				.build();
		enqueue(request, onError, body -> onSuccess.run());
	}

	@Override
	public void startLink(Consumer<LinkSession> onSuccess, Consumer<String> onError) {
		Request request = new Request.Builder()
				.url(config.apiBase() + "/api/plugin/link/start")
				.post(RequestBody.create(JSON, "{}"))
				.build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, LinkSession.class)));
	}

	@Override
	public void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<String> onError) {
		JsonObject payload = new JsonObject();
		payload.addProperty("device_code", deviceCode);
		Request request = new Request.Builder()
				.url(config.apiBase() + "/api/plugin/link/poll")
				.post(RequestBody.create(JSON, payload.toString()))
				.build();
		enqueue(request, onError, body -> {
			JsonObject parsed = gson.fromJson(body, JsonObject.class);
			if (parsed.get("pending").getAsBoolean()) {
				onPending.run();
			} else {
				onToken.accept(parsed.get("token").getAsString());
			}
		});
	}

	private Request.Builder authorized(Request.Builder builder) {
		return builder.header("Authorization", "Bearer " + config.apiToken().trim());
	}

	private void enqueue(Request request, Consumer<String> onError, Consumer<String> onBody) {
		http.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
				log.debug("geuncut request failed: {}", exception.getMessage());
				onError.accept("geuncut.app is unreachable");
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (Response managedResponse = response) {
					if (managedResponse.code() == 401) {
						onError.accept("This plugin is no longer linked. Link it again from the panel.");
						return;
					}
					if (!managedResponse.isSuccessful()) {
						onError.accept("geuncut.app error " + managedResponse.code());
						return;
					}
					onBody.accept(managedResponse.body().string());
				} catch (Exception exception) {
					log.debug("geuncut response handling failed", exception);
					onError.accept("Unexpected response from geuncut.app");
				}
			}
		});
	}
}
