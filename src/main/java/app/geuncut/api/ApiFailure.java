package app.geuncut.api;

import java.net.HttpURLConnection;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiFailure {

	private final Kind kind;
	private final int statusCode;
	private final String message;

	public static ApiFailure network(String message) {
		return new ApiFailure(Kind.NETWORK, 0, message);
	}

	public static ApiFailure http(int statusCode, String message) {
		return new ApiFailure(Kind.HTTP, statusCode, message);
	}

	public boolean isUnauthorized() {
		return kind == Kind.HTTP && statusCode == HttpURLConnection.HTTP_UNAUTHORIZED;
	}

	public enum Kind {
		NETWORK,
		HTTP
	}
}
