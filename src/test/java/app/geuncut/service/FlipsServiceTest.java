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

public class FlipsServiceTest {
	@Test
	public void fetchUnwrapsTheFlipList() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder()
				.flips(Collections.singletonList(Flip.builder().build()))
				.build();

		List<List<Flip>> received = new ArrayList<>();
		new FlipsServiceImpl(api).fetch("standard", received::add, error -> {
		});

		assertEquals(1, received.size());
		assertEquals(1, received.get(0).size());
	}

	@Test
	public void fetchSurfacesErrors() {
		MockGeUncutApi api = new MockGeUncutApi();
		List<ApiFailure> errors = new ArrayList<>();
		new FlipsServiceImpl(api).fetch("standard", flips -> {
		}, errors::add);

		assertEquals(1, errors.size());
	}
}
