package app.geuncut.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LinkSession {
	@SerializedName("user_code")
	String userCode;

	@SerializedName("device_code")
	String deviceCode;

	@SerializedName("expires_in_seconds")
	int expiresInSeconds;
}
