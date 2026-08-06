package app.geuncut.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.Flip;
import app.geuncut.dto.FlipsResponse;
import app.geuncut.dto.ScanRequest;
import app.geuncut.service.impl.FlipsServiceImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlipsServiceTest {
	private static final ScanRequest SCAN = ScanRequest.builder()
			.scanType("standard").risk("balanced").build();

	@Test
	public void nullFlipListSurfacesAsEmpty() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder().myCapital(5_000_000L).build();

		List<FlipsResponse> received = new ArrayList<>();
		new FlipsServiceImpl(api).fetch(SCAN,received::add, error -> {
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
		new FlipsServiceImpl(api).fetch(SCAN,received::add, error -> {
		});

		assertEquals(1, received.size());
		assertEquals(1, received.get(0).getFlips().size());
	}

	@Test
	public void anEmptyBoardStillCarriesTheSettingsThePanelRendersFrom() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder()
				.myMinProfit(250_000L).myMinRoi(1.5)
				.minProfitFloor(1_000_000L).minRoiFloor(3.0)
				.build();

		List<FlipsResponse> received = new ArrayList<>();
		new FlipsServiceImpl(api).fetch(SCAN, received::add, error -> {
		});

		FlipsResponse response = received.get(0);
		assertTrue(response.getFlips().isEmpty());
		assertEquals(Long.valueOf(250_000L), response.getMyMinProfit());
		assertEquals(Double.valueOf(1.5), response.getMyMinRoi());
		assertEquals(Long.valueOf(1_000_000L), response.getMinProfitFloor());
		assertEquals(Double.valueOf(3.0), response.getMinRoiFloor());
		assertNull(response.getMyCapital());
	}

	@Test
	public void theRequestReachesTheApiUntouched() {
		MockGeUncutApi api = new MockGeUncutApi();
		api.flipsResponse = FlipsResponse.builder().build();
		ScanRequest request = ScanRequest.builder()
				.scanType("fast").risk("aggressive").capital(50_000_000L)
				.minProfit(0L).minRoi(0.0).build();

		new FlipsServiceImpl(api).fetch(request, response -> {
		}, error -> {
		});

		assertEquals(request, api.lastScanRequest);
	}

	@Test
	public void fetchSurfacesErrors() {
		MockGeUncutApi api = new MockGeUncutApi();
		List<ApiFailure> errors = new ArrayList<>();
		new FlipsServiceImpl(api).fetch(SCAN,response -> {
		}, errors::add);

		assertEquals(1, errors.size());
	}
}
