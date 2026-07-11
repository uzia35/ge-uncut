package app.geuncut.dto;

import java.util.List;

import lombok.Value;

@Value
public class Movers {
	private final List<MoverEntry> risers;
	private final List<MoverEntry> fallers;
}
