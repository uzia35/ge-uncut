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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.geuncut.dto.OfferState;
import app.geuncut.tracker.OfferBatch;
import app.geuncut.tracker.OfferLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileOfferLog implements OfferLog {
	private final Gson gson;
	private final File dir;
	private final Object lock = new Object();
	private final Set<String> prepared = new HashSet<>();

	public FileOfferLog(Gson gson, File dir) {
		this.gson = gson;
		this.dir = dir;
	}

	private File fileFor(String accountHash) {
		return new File(dir, "offers-" + accountHash + ".jsonl");
	}

	private File cursorFor(String accountHash) {
		return new File(dir, "offers-" + accountHash + ".sent");
	}

	@Override
	public boolean append(String accountHash, OfferState state) {
		if (accountHash == null || state == null || state.getState() == null) {
			return false;
		}
		synchronized (lock) {
			prepare(accountHash);
			File file = fileFor(accountHash);
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			try {
				Files.write(file.toPath(), (gson.toJson(state) + "\n").getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				return true;
			} catch (IOException e) {
				prepared.remove(accountHash);
				log.warn("event=offer_log_append_failed account={} error={}", accountHash, e.getMessage());
				return false;
			}
		}
	}

	@Override
	public OfferBatch read(String accountHash, long offset, int maxEntries) {
		List<OfferState> entries = new ArrayList<>();
		if (accountHash == null || maxEntries <= 0) {
			return new OfferBatch(entries, Math.max(0, offset));
		}
		synchronized (lock) {
			prepare(accountHash);
			File file = fileFor(accountHash);
			long start = Math.max(0, offset);
			if (!file.isFile() || start >= file.length()) {
				return new OfferBatch(entries, start);
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
					OfferState state = parse(accountHash, line.toString("UTF-8"));
					line.reset();
					if (state != null) {
						entries.add(state);
					}
				}
			} catch (IOException e) {
				log.warn("event=offer_log_read_failed account={} error={}", accountHash, e.getMessage());
			}
			return new OfferBatch(entries, position);
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
				return offset < 0 || offset > size ? 0 : offset;
			} catch (IOException | NumberFormatException unusable) {
				log.warn("event=offer_log_cursor_unreadable account={}", accountHash);
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
				log.warn("event=offer_log_cursor_write_failed account={} error={}", accountHash, e.getMessage());
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException ignored) {
				}
			}
		}
	}

	private OfferState parse(String accountHash, String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			OfferState state = gson.fromJson(trimmed, OfferState.class);
			return state != null && state.getState() != null ? state : null;
		} catch (RuntimeException malformed) {
			log.warn("event=offer_log_bad_line account={}", accountHash);
			return null;
		}
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
			log.warn("event=offer_log_tail_unreadable account={} error={}", accountHash, e.getMessage());
			return;
		}
		try {
			Files.write(file.toPath(), "\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
			log.warn("event=offer_log_torn_line_sealed account={}", accountHash);
		} catch (IOException e) {
			log.warn("event=offer_log_seal_failed account={} error={}", accountHash, e.getMessage());
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
