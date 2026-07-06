package app.geuncut.model;

import java.time.Instant;

import lombok.Value;

@Value
public class Purchase {
	private final int quantity;
	private final Instant boughtAt;
}
