package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LinkSession {
	@SerializedName("user_code")
	private final String userCode;

	@SerializedName("device_code")
	private final String deviceCode;

	@SerializedName("expires_in_seconds")
	private final int expiresInSeconds;
}
