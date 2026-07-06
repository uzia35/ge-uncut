package app.geuncut.api;

import java.net.HttpURLConnection;

import lombok.Value;

/**
 * A failed API call. statusCode is only meaningful for HTTP failures.
 */
@Value
public class ApiFailure {
	public enum Kind {
		NETWORK,
		HTTP
	}

	Kind kind;
	int statusCode;
	String message;

	public static ApiFailure network(String message) {
		return new ApiFailure(Kind.NETWORK, 0, message);
	}

	public static ApiFailure http(int statusCode, String message) {
		return new ApiFailure(Kind.HTTP, statusCode, message);
	}

	public boolean isUnauthorized() {
		return kind == Kind.HTTP && statusCode == HttpURLConnection.HTTP_UNAUTHORIZED;
	}
}
