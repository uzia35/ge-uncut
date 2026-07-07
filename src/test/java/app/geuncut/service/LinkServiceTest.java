package app.geuncut.service;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

import app.geuncut.api.ApiFailure;
import app.geuncut.dto.LinkSession;
import app.geuncut.service.impl.LinkServiceImpl;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class LinkServiceTest {
	private MockGeUncutApi api;
	private ConfigManager configManager;
	private ScheduledExecutorService executor;
	private LinkService service;

	private final List<String> shownCodes = new ArrayList<>();
	private final List<ApiFailure> errors = new ArrayList<>();
	private int linkedCalls;

	@Before
	public void setUp() {
		api = new MockGeUncutApi();
		configManager = mock(ConfigManager.class);
		executor = mock(ScheduledExecutorService.class);
		service = new LinkServiceImpl(api, configManager, executor);

		api.session = LinkSession.builder()
				.userCode("ABCD-1234")
				.deviceCode("device-secret")
				.expiresInSeconds(600)
				.build();
	}

	private Runnable begin() {
		service.begin(shownCodes::add, () -> linkedCalls++, errors::add);
		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(tick.capture(), anyLong(), anyLong(), any(TimeUnit.class));
		return tick.getValue();
	}

	@Test
	public void beginSurfacesTheUserCodeAndStartsPolling() {
		begin();
		assertEquals(1, shownCodes.size());
		assertEquals("ABCD-1234", shownCodes.get(0));
		assertTrue(service.isPairing());
	}

	@Test
	public void pendingPollsKeepTheSessionAlive() {
		Runnable poll = begin();
		poll.run();
		poll.run();

		assertEquals(2, api.pollCount);
		assertEquals(0, linkedCalls);
		assertTrue(service.isPairing());
		verify(configManager, never()).setConfiguration(any(), any(), any(String.class));
	}

	@Test
	public void claimedPollWritesTokenAndFinishes() {
		Runnable poll = begin();
		api.linkToken = "gu_delivered";
		poll.run();

		verify(configManager).setConfiguration("geuncut", "apiToken", "gu_delivered");
		assertEquals(1, linkedCalls);
		assertFalse(service.isPairing());
	}

	@Test
	public void pollErrorCancelsAndReports() {
		Runnable poll = begin();
		api.linkPending = false;
		poll.run();

		assertEquals(1, errors.size());
		assertFalse(service.isPairing());
		verify(configManager, never()).setConfiguration(any(), any(), any(String.class));
	}

	@Test
	public void startFailureReportsWithoutPolling() {
		api.session = null;
		service.begin(shownCodes::add, () -> linkedCalls++, errors::add);

		assertEquals(1, errors.size());
		assertFalse(service.isPairing());
		verify(executor, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
	}

	@Test
	public void cancelStopsTheSession() {
		begin();
		service.cancel();
		assertFalse(service.isPairing());
	}

	@Test
	public void staleSessionPollNeverWritesAToken() {
		Runnable stalePoll = begin();
		api.deferNextPoll = true;
		stalePoll.run();

		// User abandons session 1 and starts a fresh session 2.
		service.cancel();
		api.session = LinkSession.builder()
				.userCode("WXYZ-9999")
				.deviceCode("device-2")
				.expiresInSeconds(600)
				.build();
		service.begin(shownCodes::add, () -> linkedCalls++, errors::add);

		// The abandoned session-1 poll finally delivers a token.
		api.linkToken = "stale-token";
		api.firePendingPoll();

		verify(configManager, never()).setConfiguration(any(), any(), any(String.class));
		assertEquals(0, linkedCalls);
		assertTrue(service.isPairing());
	}
}
