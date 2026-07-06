package app.geuncut.api.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
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
import okhttp3.Interceptor;
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
	private static final int MAX_ATTEMPTS = 3;
	private static final long RETRY_BASE_DELAY_MS = 300;

	private final OkHttpClient http;
	private final Gson gson;
	private final GeUncutConfig config;
	private final ScheduledExecutorService executor;

	@Inject
	public HttpGeUncutApi(OkHttpClient http, Gson gson, GeUncutConfig config, ScheduledExecutorService executor) {
		// Plugin-scoped client derived from RuneLite's shared instance: same
		// connection pool and dispatcher, but the auth interceptor only ever
		// sees this plugin's requests.
		this.http = http.newBuilder().addInterceptor(this::authorize).build();
		this.gson = gson;
		this.config = config;
		this.executor = executor;
	}

	private Response authorize(Interceptor.Chain chain) throws IOException {
		Request request = chain.request();
		String token = config.apiToken().trim();
		if (token.isEmpty() || isAnonymous(request)) {
			return chain.proceed(request);
		}
		return chain.proceed(request.newBuilder()
				.header("Authorization", "Bearer " + token)
				.build());
	}

	private static boolean isAnonymous(Request request) {
		// Pairing runs before a token exists; those endpoints never carry one.
		return request.url().encodedPath().startsWith("/api/plugin/link/");
	}

	@Override
	public void fetchFlips(String scanType, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl url = HttpUrl.parse(config.apiBase() + "/api/plugin/flips").newBuilder()
				.addQueryParameter("scan_type", scanType)
				.build();
		Request request = new Request.Builder().url(url).get().build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, FlipsResponse.class)));
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError) {
		JsonObject payload = new JsonObject();
		payload.add("events", gson.toJsonTree(events));
		Request request = new Request.Builder()
				.url(config.apiBase() + "/api/plugin/ge-events")
				.post(RequestBody.create(JSON, payload.toString()))
				.build();
		enqueue(request, onError, body -> onSuccess.run());
	}

	@Override
	public void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError) {
		Request request = new Request.Builder()
				.url(config.apiBase() + "/api/plugin/link/start")
				.post(RequestBody.create(JSON, "{}"))
				.build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, LinkSession.class)));
	}

	@Override
	public void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError) {
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

	private void enqueue(Request request, Consumer<ApiFailure> onError, Consumer<String> onBody) {
		attempt(request, 1, onError, onBody);
	}

	private void attempt(Request request, int attemptNumber, Consumer<ApiFailure> onError, Consumer<String> onBody) {
		http.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
				retryOrFail(ApiFailure.network("geuncut.app is unreachable"), exception.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (Response managedResponse = response) {
					int statusCode = managedResponse.code();
					if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
						fail(ApiFailure.http(statusCode,
								"This plugin is no longer linked. Link it again from the panel."));
						return;
					}
					if (isTransient(statusCode)) {
						retryOrFail(ApiFailure.http(statusCode, "geuncut.app error " + statusCode),
								"status " + statusCode);
						return;
					}
					if (!managedResponse.isSuccessful()) {
						fail(ApiFailure.http(statusCode, "geuncut.app error " + statusCode));
						return;
					}
					onBody.accept(managedResponse.body().string());
				} catch (Exception exception) {
					log.debug("event=api_response_unreadable path={} error={}",
							request.url().encodedPath(), exception.toString());
					fail(ApiFailure.network("Unexpected response from geuncut.app"));
				}
			}

			private void retryOrFail(ApiFailure failure, String reason) {
				if (attemptNumber >= MAX_ATTEMPTS) {
					fail(failure);
					return;
				}
				long delayMs = RETRY_BASE_DELAY_MS * attemptNumber;
				log.debug("event=api_retry path={} attempt={} delay_ms={} reason=\"{}\"",
						request.url().encodedPath(), attemptNumber, delayMs, reason);
				executor.schedule(
						() -> attempt(request, attemptNumber + 1, onError, onBody),
						delayMs, TimeUnit.MILLISECONDS);
			}

			private void fail(ApiFailure failure) {
				log.debug("event=api_failure path={} status={} attempts={} message=\"{}\"",
						request.url().encodedPath(), failure.getStatusCode(), attemptNumber, failure.getMessage());
				onError.accept(failure);
			}
		});
	}

	private static boolean isTransient(int statusCode) {
		return statusCode == HttpURLConnection.HTTP_BAD_GATEWAY
				|| statusCode == HttpURLConnection.HTTP_UNAVAILABLE
				|| statusCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
	}
}
