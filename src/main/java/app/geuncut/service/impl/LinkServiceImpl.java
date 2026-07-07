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
	private boolean starting;

	@Inject
	public LinkServiceImpl(GeUncutApi api, GeUncutConfig config, ConfigManager configManager,
			ScheduledExecutorService executor) {
		this.api = api;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;
	}

	// session and poller are mutated from the UI thread (begin, cancel) and the
	// scheduler thread (poll callbacks); every transition is synchronized so a
	// cancel can never interleave with a start or a poll reading half a state.

	@Override
	public synchronized boolean isPairing() {
		return session != null;
	}

	@Override
	public synchronized void openClaimPage() {
		if (session != null) {
			LinkBrowser.browse(config.apiBase() + "/link?code=" + session.getUserCode());
		}
	}

	@Override
	public void begin(Consumer<String> onCode, Runnable onLinked, Consumer<ApiFailure> onError) {
		synchronized (this) {
			if (session != null) {
				openClaimPage();
				return;
			}
			if (starting) {
				return;
			}
			starting = true;
		}
		api.startLink(started -> {
			synchronized (this) {
				starting = false;
				if (session != null) {
					return;
				}
				session = started;
				poller = executor.scheduleWithFixedDelay(
						() -> poll(onLinked, onError), POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
			}
			log.debug("event=link_started expires_in={}s", started.getExpiresInSeconds());
			onCode.accept(started.getUserCode());
		}, failure -> {
			synchronized (this) {
				starting = false;
			}
			onError.accept(failure);
		});
	}

	@Override
	public synchronized void cancel() {
		session = null;
		if (poller != null) {
			poller.cancel(false);
			poller = null;
		}
	}

	private void poll(Runnable onLinked, Consumer<ApiFailure> onError) {
		LinkSession active;
		synchronized (this) {
			if (session == null) {
				return;
			}
			active = session;
		}
		api.pollLink(active.getDeviceCode(),
				token -> {
					synchronized (this) {
						if (session != active) {
							return;
						}
					}
					configManager.setConfiguration(GeUncutConfig.GROUP, "apiToken", token);
					log.debug("event=link_completed");
					cancel();
					onLinked.run();
				},
				() -> {
				},
				failure -> {
					synchronized (this) {
						if (session != active) {
							return;
						}
					}
					log.debug("event=link_poll_stopped kind={} status={} message=\"{}\"",
							failure.getKind(), failure.getStatusCode(), failure.getMessage());
					cancel();
					onError.accept(failure);
				});
	}
}
