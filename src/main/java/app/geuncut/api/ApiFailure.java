package app.geuncut.api;

import java.net.HttpURLConnection;

import lombok.Value;

/**
 * A failed API call. statusCode is the HTTP status, or NETWORK_FAILURE when
 * the request never got a response.
 */
@Value
public class ApiFailure {
	public static final int NETWORK_FAILURE = 0;

	int statusCode;
	String message;

	public static ApiFailure network(String message) {
		return new ApiFailure(NETWORK_FAILURE, message);
	}

	public static ApiFailure http(int statusCode, String message) {
		return new ApiFailure(statusCode, message);
	}

	public boolean isUnauthorized() {
		return statusCode == HttpURLConnection.HTTP_UNAUTHORIZED;
	}
}
