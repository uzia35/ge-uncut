package app.geuncut.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.Flip;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.service.impl.FlipsServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlipsServiceTest {
	@Test
	public void nullFlipListSurfacesAsEmpty() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder().myCapital(5_000_000L).build();

		List<FlipsResponse> received = new ArrayList<>();
		new FlipsServiceImpl(api).fetch("standard", "balanced", null, received::add, error -> {
		});

		assertEquals(1, received.size());
		assertTrue(received.get(0).getFlips().isEmpty());
		assertEquals(Long.valueOf(5_000_000L), received.get(0).getMyCapital());
	}

	@Test
	public void fetchPassesTheResponseThrough() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder()
				.flips(Collections.singletonList(Flip.builder().build()))
				.build();

		List<FlipsResponse> received = new ArrayList<>();
		new FlipsServiceImpl(api).fetch("standard", "balanced", null, received::add, error -> {
		});

		assertEquals(1, received.size());
		assertEquals(1, received.get(0).getFlips().size());
	}

	@Test
	public void fetchSurfacesErrors() {
		MockGeUncutApi api = new MockGeUncutApi();
		List<ApiFailure> errors = new ArrayList<>();
		new FlipsServiceImpl(api).fetch("standard", "balanced", null, response -> {
		}, errors::add);

		assertEquals(1, errors.size());
	}
}
