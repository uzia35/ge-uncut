package app.geuncut.tracker.impl;

import java.time.Instant;

import lombok.Value;

@Value
class Purchase {
	int quantity;
	Instant boughtAt;
}
