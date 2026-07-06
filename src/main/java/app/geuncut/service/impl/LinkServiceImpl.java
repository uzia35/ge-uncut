package app.geuncut.service.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

import app.geuncut.api.ApiFailure;
import app.geuncut.api.GeUncutApi;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.dto.LinkSession;
import app.geuncut.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Device-link pairing state machine. Starts a session, surfaces the short
 * code for the user to type at geuncut.app/link, polls until the claim lands,
 * then writes the delivered token into the plugin config itself.
 */
@Slf4j
@Singleton
public class LinkServiceImpl implements LinkService {
	private static final int POLL_SECONDS = 5;

	private final GeUncutApi api;
	private final GeUncutConfig config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;

	private LinkSession session;
	private ScheduledFuture<?> poller;

	@Inject
	public LinkServiceImpl(GeUncutApi api, GeUncutConfig config, ConfigManager configManager,
			ScheduledExecutorService executor) {
		this.api = api;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;
	}

	@Override
	public boolean isPairing() {
		return session != null;
	}

	@Override
	public void openClaimPage() {
		if (session != null) {
			LinkBrowser.browse(config.apiBase() + "/link?code=" + session.getUserCode());
		}
	}

	@Override
	public void begin(Consumer<String> onCode, Runnable onLinked, Consumer<ApiFailure> onError) {
		if (session != null) {
			openClaimPage();
			return;
		}
		api.startLink(started -> {
			session = started;
			log.debug("event=link_started expires_in={}s", started.getExpiresInSeconds());
			onCode.accept(started.getUserCode());
			poller = executor.scheduleWithFixedDelay(
					() -> poll(onLinked, onError), POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
		}, onError);
	}

	@Override
	public void cancel() {
		session = null;
		if (poller != null) {
			poller.cancel(false);
			poller = null;
		}
	}

	private void poll(Runnable onLinked, Consumer<ApiFailure> onError) {
		if (session == null) {
			return;
		}
		api.pollLink(session.getDeviceCode(),
				token -> {
					configManager.setConfiguration(GeUncutConfig.GROUP, "apiToken", token);
					log.debug("event=link_completed");
					cancel();
					onLinked.run();
				},
				() -> {
				},
				failure -> {
					log.debug("event=link_poll_stopped status={} message=\"{}\"", failure.getStatusCode(), failure.getMessage());
					cancel();
					onError.accept(failure);
				});
	}
}
