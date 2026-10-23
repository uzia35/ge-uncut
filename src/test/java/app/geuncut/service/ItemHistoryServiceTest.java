package app.geuncut.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;
import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.dto.ItemHistory;
import app.geuncut.service.ItemHistoryService.Status;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ItemHistoryServiceTest {
	private final List<Consumer<ItemHistory>> successes = new ArrayList<>();
	private final List<Consumer<ApiFailure>> errors = new ArrayList<>();
	private final AtomicLong clock = new AtomicLong();
	private final ItemHistory empty = new ItemHistory(Collections.emptyList(), Collections.emptyList());
	private ItemHistoryService service;

	@Before
	public void setup() {
		GeUncutApi api = mock(GeUncutApi.class);
		doAnswer(call -> {
			successes.add(call.getArgument(1));
			errors.add(call.getArgument(2));
			return null;
		}).when(api).fetchItemHistory(anyInt(), any(), any());
		service = new ItemHistoryService(api, clock::get);
	}

	@Test
	public void repeatedTicksShareOneRequestAndRefreshAfterOneMinute() {
		assertEquals(Status.LOADING, service.select(536).getStatus());
		service.select(536);
		assertEquals(1, successes.size());
		successes.get(0).accept(empty);
		assertSame(empty, service.select(536).getHistory());
		clock.set(59_999);
		service.select(536);
		assertEquals(1, successes.size());
		clock.set(60_000);
		assertEquals(Status.LOADING, service.select(536).getStatus());
		assertEquals(2, successes.size());
	}

	@Test
	public void oldSuccessAndFailureCannotOverwriteAnABASearch() {
		service.select(536);
		service.select(4151);
		service.select(536);
		successes.get(0).accept(empty);
		errors.get(1).accept(ApiFailure.network("old item"));
		assertEquals(Status.LOADING, service.select(536).getStatus());
		successes.get(2).accept(empty);
		errors.get(0).accept(ApiFailure.network("old request"));
		assertEquals(Status.READY, service.select(536).getStatus());
		assertEquals(3, successes.size());
	}

	@Test
	public void clearAndReopenRejectsThePreviousRequest() {
		service.select(536);
		service.clear();
		service.clear();
		successes.get(0).accept(empty);
		assertEquals(Status.LOADING, service.select(536).getStatus());
		successes.get(1).accept(empty);
		assertEquals(Status.READY, service.select(536).getStatus());
	}

	@Test
	public void errorWaitsForAnExplicitRetryAndIgnoresWrongItemRetry() {
		service.select(536);
		errors.get(0).accept(ApiFailure.http(503, "unavailable"));
		clock.set(120_000);
		assertEquals(Status.ERROR, service.select(536).getStatus());
		service.retry(4151);
		assertEquals(1, successes.size());
		service.retry(536);
		service.retry(536);
		assertEquals(2, successes.size());
		successes.get(1).accept(empty);
		assertEquals(Status.READY, service.select(536).getStatus());
	}
}
