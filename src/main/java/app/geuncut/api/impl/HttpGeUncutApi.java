package app.geuncut.api.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.GeOffer;
import app.geuncut.dto.GeTradeEvent;
import app.geuncut.dto.LinkSession;
import app.geuncut.dto.PositionsResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import okhttp3.ResponseBody;

/**
 * HTTP transport to geuncut.app. Nothing but HTTP lives here; payload shapes
 * are the dto package, business rules are the service layer.
 */
@Slf4j
@Singleton
public class HttpGeUncutApi implements GeUncutApi {
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int HTTP_TOO_MANY_REQUESTS = 429;
	private static final int MAX_ATTEMPTS = 3;
	private static final long RETRY_BASE_DELAY_MS = 300;
	private static final long RETRY_JITTER_MS = 150;

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

	// Null when apiBase is blank/malformed (self-host typo), so callers surface it as
	// an ApiFailure rather than throwing out of the request-building thread. Trailing
	// slashes are trimmed so "host/" + "/path" never yields a "//".
	private HttpUrl resolve(String path) {
		String base = config.apiBase();
		if (base == null) {
			return null;
		}
		base = base.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return HttpUrl.parse(base + path);
	}

	@Override
	public void fetchFlips(String scanType, String risk, Consumer<FlipsResponse> onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl base = resolve("/api/plugin/flips");
		if (base == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		HttpUrl.Builder url = base.newBuilder().addQueryParameter("scan_type", scanType);
		if (risk != null && !risk.isEmpty()) {
			url.addQueryParameter("risk", risk);
		}
		Request request = new Request.Builder().url(url.build()).get().build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, FlipsResponse.class)));
	}

	@Override
	public void fetchPositions(Consumer<PositionsResponse> onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl url = resolve("/api/plugin/positions");
		if (url == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		Request request = new Request.Builder().url(url).get().build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, PositionsResponse.class)));
	}

	@Override
	public void postGeEvents(List<GeTradeEvent> events, Runnable onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl url = resolve("/api/plugin/ge-events");
		if (url == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		JsonObject payload = new JsonObject();
		payload.add("events", gson.toJsonTree(events));
		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(JSON, payload.toString()))
				.build();
		enqueue(request, onError, body -> onSuccess.run());
	}

	@Override
	public void postOffers(String accountHash, List<GeOffer> offers, String syncedAt, Runnable onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl url = resolve("/api/plugin/offers");
		if (url == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("account_hash", accountHash);
		payload.addProperty("synced_at", syncedAt);
		payload.add("offers", gson.toJsonTree(offers));
		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(JSON, payload.toString()))
				.build();
		enqueue(request, onError, body -> onSuccess.run());
	}

	@Override
	public void startLink(Consumer<LinkSession> onSuccess, Consumer<ApiFailure> onError) {
		HttpUrl url = resolve("/api/plugin/link/start");
		if (url == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(JSON, "{}"))
				.build();
		enqueue(request, onError, body -> onSuccess.accept(gson.fromJson(body, LinkSession.class)));
	}

	@Override
	public void pollLink(String deviceCode, Consumer<String> onToken, Runnable onPending, Consumer<ApiFailure> onError) {
		HttpUrl url = resolve("/api/plugin/link/poll");
		if (url == null) {
			onError.accept(ApiFailure.network("Invalid geuncut.app URL"));
			return;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("device_code", deviceCode);
		Request request = new Request.Builder()
				.url(url)
				.post(RequestBody.create(JSON, payload.toString()))
				.build();
		enqueue(request, onError, body -> {
			JsonObject parsed = gson.fromJson(body, JsonObject.class);
			if (parsed == null || !parsed.has("pending")) {
				throw new JsonParseException("missing 'pending' in link poll response");
			}
			if (parsed.get("pending").getAsBoolean()) {
				onPending.run();
			} else if (parsed.has("token")) {
				onToken.accept(parsed.get("token").getAsString());
			} else {
				throw new JsonParseException("missing 'token' in link poll response");
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
					ResponseBody responseBody = managedResponse.body();
					if (responseBody == null) {
						fail(ApiFailure.network("Empty response from geuncut.app"));
						return;
					}
					// Only the read + parse are guarded here; a bug in the caller's own
					// success handler must propagate, not be masked as a transport error.
					onBody.accept(responseBody.string());
				} catch (IOException | JsonParseException exception) {
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
				// Jitter prevents a fleet of clients that failed together (server
				// deploy, brief outage) from retrying in lockstep.
				long delayMs = RETRY_BASE_DELAY_MS * attemptNumber
						+ ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS);
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
		return statusCode == HTTP_TOO_MANY_REQUESTS
				|| statusCode == HttpURLConnection.HTTP_BAD_GATEWAY
				|| statusCode == HttpURLConnection.HTTP_UNAVAILABLE
				|| statusCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
	}
}
