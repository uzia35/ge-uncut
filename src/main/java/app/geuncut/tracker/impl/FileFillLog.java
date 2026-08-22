package app.geuncut.tracker.impl;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.FillBatch;
import app.geuncut.tracker.FillLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileFillLog implements FillLog {
	private final Gson gson;
	private final File dir;
	private final Object lock = new Object();
	private final Set<String> prepared = new HashSet<>();
	private final Map<String, Long> entryCounts = new HashMap<>();

	public FileFillLog(Gson gson, File dir) {
		this.gson = gson;
		this.dir = dir;
	}

	private File fileFor(String accountHash) {
		return new File(dir, "fills-" + accountHash + ".jsonl");
	}

	private File cursorFor(String accountHash) {
		return new File(dir, "fills-" + accountHash + ".sent");
	}

	@Override
	public void append(String accountHash, GeTradeEvent event) {
		if (accountHash == null || event == null || event.getIdempotencyKey() == null) {
			return;
		}
		synchronized (lock) {
			prepare(accountHash);
			File file = fileFor(accountHash);
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			long seq = entryCount(accountHash);
			GeTradeEvent numbered = event.toBuilder().seq(seq).build();
			try {
				Files.write(file.toPath(), (gson.toJson(numbered) + "\n").getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				entryCounts.put(accountHash, seq + 1);
			} catch (IOException e) {
				log.warn("event=fill_log_append_failed account={} error={}", accountHash, e.getMessage());
			}
		}
	}

	@Override
	public FillBatch read(String accountHash, long offset, int maxEntries) {
		List<GeTradeEvent> entries = new ArrayList<>();
		if (accountHash == null || maxEntries <= 0) {
			return new FillBatch(entries, Math.max(0, offset));
		}
		synchronized (lock) {
			prepare(accountHash);
			File file = fileFor(accountHash);
			long start = Math.max(0, offset);
			if (!file.isFile() || start >= file.length()) {
				return new FillBatch(entries, start);
			}
			long position = start;
			try (InputStream raw = new FileInputStream(file);
					BufferedInputStream in = new BufferedInputStream(raw)) {
				skipFully(in, start);
				ByteArrayOutputStream line = new ByteArrayOutputStream();
				int read;
				while (entries.size() < maxEntries && (read = in.read()) >= 0) {
					line.write(read);
					if (read != '\n') {
						continue;
					}
					position += line.size();
					GeTradeEvent event = parse(accountHash, line.toString("UTF-8"));
					line.reset();
					if (event != null) {
						entries.add(event);
					}
				}
			} catch (IOException e) {
				log.warn("event=fill_log_read_failed account={} error={}", accountHash, e.getMessage());
			}
			return new FillBatch(entries, position);
		}
	}

	@Override
	public long deliveredOffset(String accountHash) {
		if (accountHash == null) {
			return 0;
		}
		synchronized (lock) {
			File cursor = cursorFor(accountHash);
			if (!cursor.isFile()) {
				return 0;
			}
			try {
				String raw = new String(Files.readAllBytes(cursor.toPath()), StandardCharsets.UTF_8).trim();
				long offset = Long.parseLong(raw);
				long size = fileFor(accountHash).length();
				return offset < 0 ? 0 : Math.min(offset, size);
			} catch (IOException | NumberFormatException unusable) {
				log.warn("event=fill_log_cursor_unreadable account={}", accountHash);
				return 0;
			}
		}
	}

	@Override
	public void markDelivered(String accountHash, long offset) {
		if (accountHash == null) {
			return;
		}
		synchronized (lock) {
			if (offset <= deliveredOffset(accountHash)) {
				return;
			}
			File cursor = cursorFor(accountHash);
			File parent = cursor.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			Path target = cursor.toPath();
			Path tmp = new File(parent, cursor.getName() + ".tmp").toPath();
			try {
				Files.write(tmp, Long.toString(offset).getBytes(StandardCharsets.UTF_8));
				try {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicUnsupported) {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				log.warn("event=fill_log_cursor_write_failed account={} error={}", accountHash, e.getMessage());
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private GeTradeEvent parse(String accountHash, String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			GeTradeEvent event = gson.fromJson(trimmed, GeTradeEvent.class);
			return event != null && event.getIdempotencyKey() != null ? event : null;
		} catch (RuntimeException malformed) {
			log.warn("event=fill_log_bad_line account={}", accountHash);
			return null;
		}
	}

	private long entryCount(String accountHash) {
		Long known = entryCounts.get(accountHash);
		if (known != null) {
			return known;
		}
		long counted = 0;
		File file = fileFor(accountHash);
		if (file.isFile()) {
			try (InputStream raw = new FileInputStream(file);
					BufferedInputStream in = new BufferedInputStream(raw)) {
				int read;
				while ((read = in.read()) >= 0) {
					if (read == '\n') {
						counted++;
					}
				}
			} catch (IOException e) {
				log.warn("event=fill_log_count_failed account={} error={}", accountHash, e.getMessage());
			}
		}
		entryCounts.put(accountHash, counted);
		return counted;
	}

	private void prepare(String accountHash) {
		if (!prepared.add(accountHash)) {
			return;
		}
		File file = fileFor(accountHash);
		if (!file.isFile() || file.length() == 0) {
			return;
		}
		try (RandomAccessFile handle = new RandomAccessFile(file, "r")) {
			handle.seek(file.length() - 1);
			if (handle.read() == '\n') {
				return;
			}
		} catch (IOException e) {
			log.warn("event=fill_log_tail_unreadable account={} error={}", accountHash, e.getMessage());
			return;
		}
		try {
			Files.write(file.toPath(), "\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
			log.warn("event=fill_log_torn_line_sealed account={}", accountHash);
		} catch (IOException e) {
			log.warn("event=fill_log_seal_failed account={} error={}", accountHash, e.getMessage());
		}
	}

	private static void skipFully(InputStream in, long count) throws IOException {
		long remaining = count;
		while (remaining > 0) {
			long skipped = in.skip(remaining);
			if (skipped <= 0) {
				if (in.read() < 0) {
					return;
				}
				skipped = 1;
			}
			remaining -= skipped;
		}
	}
}
