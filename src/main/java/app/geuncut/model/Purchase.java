package app.geuncut.model;

import java.time.Instant;

import lombok.Value;

@Value
public class Purchase {
	int quantity;
	Instant boughtAt;
}
