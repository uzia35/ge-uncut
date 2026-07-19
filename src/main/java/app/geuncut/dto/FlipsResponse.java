package app.geuncut.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FlipsResponse {
	private final List<Flip> flips;

	// The bankroll saved on the website; null when unlinked (or an old server).
	@SerializedName("my_capital")
	private final Long myCapital;
}
