package app.geuncut.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

@Value
public class Movers {
	private final List<MoverEntry> risers;
	private final List<MoverEntry> fallers;

	@SerializedName("volume_spikes")
	private final List<MoverEntry> volumeSpikes;
}
