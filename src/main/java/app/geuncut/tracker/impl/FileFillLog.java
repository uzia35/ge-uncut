package app.geuncut.tracker.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.FillLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileFillLog implements FillLog {
	private final Gson gson;
	private final File dir;
	private final Object lock = new Object();

	public FileFillLog(Gson gson, File dir) {
		this.gson = gson;
		this.dir = dir;
	}

	private File fileFor(String accountHash) {
		return new File(dir, "fills-" + accountHash + ".jsonl");
	}

	@Override
	public void append(String accountHash, GeTradeEvent event) {
		if (accountHash == null || event == null || event.getIdempotencyKey() == null) {
			return;
		}
		synchronized (lock) {
			File file = fileFor(accountHash);
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			try {
				Files.write(file.toPath(), (gson.toJson(event) + "\n").getBytes(StandardCharsets.UTF_8),
						java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			} catch (IOException e) {
				log.warn("event=fill_log_append_failed account={} error={}", accountHash, e.getMessage());
			}
		}
	}

	@Override
	public List<GeTradeEvent> pending(String accountHash) {
		if (accountHash == null) {
			return new ArrayList<>();
		}
		synchronized (lock) {
			return readAll(accountHash);
		}
	}

	@Override
	public void ack(String accountHash, Collection<String> idempotencyKeys) {
		if (accountHash == null || idempotencyKeys == null || idempotencyKeys.isEmpty()) {
			return;
		}
		synchronized (lock) {
			Set<String> acked = new HashSet<>(idempotencyKeys);
			List<GeTradeEvent> remaining = new ArrayList<>();
			for (GeTradeEvent event : readAll(accountHash)) {
				if (!acked.contains(event.getIdempotencyKey())) {
					remaining.add(event);
				}
			}
			rewrite(accountHash, remaining);
		}
	}

	private List<GeTradeEvent> readAll(String accountHash) {
		File file = fileFor(accountHash);
		if (!file.isFile()) {
			return new ArrayList<>();
		}
		Map<String, GeTradeEvent> byKey = new LinkedHashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				try {
					GeTradeEvent event = gson.fromJson(line, GeTradeEvent.class);
					if (event != null && event.getIdempotencyKey() != null) {
						byKey.put(event.getIdempotencyKey(), event);
					}
				} catch (RuntimeException parse) {
					log.warn("event=fill_log_bad_line account={}", accountHash);
				}
			}
		} catch (IOException e) {
			log.warn("event=fill_log_read_failed account={} error={}", accountHash, e.getMessage());
		}
		return new ArrayList<>(byKey.values());
	}

	private void rewrite(String accountHash, List<GeTradeEvent> events) {
		File file = fileFor(accountHash);
		if (events.isEmpty()) {
			file.delete();
			return;
		}
		StringBuilder body = new StringBuilder();
		for (GeTradeEvent event : events) {
			body.append(gson.toJson(event)).append('\n');
		}
		Path target = file.toPath();
		Path tmp = new File(file.getParentFile(), file.getName() + ".tmp").toPath();
		try {
			Files.write(tmp, body.toString().getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicUnsupported) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.warn("event=fill_log_rewrite_failed account={} error={}", accountHash, e.getMessage());
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
		}
	}
}
