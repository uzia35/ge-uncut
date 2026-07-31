package app.geuncut.service.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import app.geuncut.service.SyncService;

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

	protected final String accountHash() {
		return accountHashSupplier != null ? accountHashSupplier.get() : null;
	}

	protected abstract void flush();

	protected abstract void onStop();
}
