package app.geuncut.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.PositionsResponse;
import app.geuncut.dto.PositionsSummary;
import app.geuncut.service.impl.PositionsServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PositionsServiceTest {
	@Test
	public void fetchPassesTheResponseThrough() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.positionsResponse = PositionsResponse.builder()
				.positions(Collections.emptyList())
				.summary(PositionsSummary.builder().build())
				.build();

		List<PositionsResponse> received = new ArrayList<>();
		new PositionsServiceImpl(api).fetch(received::add, error -> {
		});

		assertEquals(1, received.size());
		assertSame(api.positionsResponse, received.get(0));
	}

	@Test
	public void fetchSurfacesErrors() {
		MockGeUncutApi api = new MockGeUncutApi();
		List<ApiFailure> errors = new ArrayList<>();
		new PositionsServiceImpl(api).fetch(response -> {
		}, errors::add);

		assertEquals(1, errors.size());
	}
}
