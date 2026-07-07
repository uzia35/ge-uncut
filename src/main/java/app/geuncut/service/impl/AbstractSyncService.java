package app.geuncut.service.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import app.geuncut.service.SyncService;

/**
 * Shared lifecycle for the services that batch state to geuncut.app on a timer.
 * Owns the scheduled flush loop and the account-hash supplier; subclasses own
 * what accumulates between flushes and how a flush and a stop behave.
 */
abstract class AbstractSyncService implements SyncService {
	private final ScheduledExecutorService executor;
	private final int flushIntervalSeconds;

	private volatile ScheduledFuture<?> flusher;
	private volatile Supplier<String> accountHashSupplier;

	protected AbstractSyncService(ScheduledExecutorService executor, int flushIntervalSeconds) {
		this.executor = executor;
		this.flushIntervalSeconds = flushIntervalSeconds;
	}

	@Override
	public final void start(Supplier<String> accountHashSupplier) {
		this.accountHashSupplier = accountHashSupplier;
		// Guard against a start without an intervening stop leaking a second loop.
		if (flusher != null) {
			flusher.cancel(false);
		}
		flusher = executor.scheduleWithFixedDelay(
				this::flush, flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
	}

	@Override
	public final void stop() {
		if (flusher != null) {
			flusher.cancel(false);
			flusher = null;
		}
		onStop();
	}

	/** Account hash of the logged-in player, or null before start. */
	protected final String accountHash() {
		return accountHashSupplier != null ? accountHashSupplier.get() : null;
	}

	/** Push whatever has accumulated. Runs on the scheduler thread. */
	protected abstract void flush();

	/** Release state after the flush loop is cancelled. */
	protected abstract void onStop();
}
