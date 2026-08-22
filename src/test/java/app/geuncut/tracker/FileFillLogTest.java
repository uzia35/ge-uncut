package app.geuncut.tracker;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.impl.FileFillLog;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileFillLogTest {
	private File dir;
	private Gson gson;

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("filllog").toFile();
		gson = new Gson();
	}

	@After
	public void tearDown() {
		for (File f : dir.listFiles() != null ? dir.listFiles() : new File[0]) {
			f.delete();
		}
		dir.delete();
	}

	private static GeTradeEvent event(String key, int qty) {
		return GeTradeEvent.builder()
				.accountHash("acct-1").idempotencyKey(key).clientEventId(key)
				.itemId(20997).side("buy").quantity(qty).priceEach(1000).slot(0)
				.occurredAt("2026-07-05T12:00:00Z").build();
	}

	private File logFile(String accountHash) {
		return new File(dir, "fills-" + accountHash + ".jsonl");
	}

	private int lineCount(String accountHash) throws Exception {
		File file = logFile(accountHash);
		if (!file.isFile()) {
			return 0;
		}
		return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).size();
	}

	@Test
	public void appendThenReadRoundTripsTheFill() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 7));

		FillBatch batch = log.read("acct-1", 0, 50);
		assertEquals(1, batch.getEntries().size());
		assertEquals("k1", batch.getEntries().get(0).getIdempotencyKey());
		assertEquals(7, batch.getEntries().get(0).getQuantity());
		assertEquals("buy", batch.getEntries().get(0).getSide());
		assertEquals(logFile("acct-1").length(), batch.getNextOffset());
	}

	@Test
	public void appendStampsTheOrdinalOfEveryEntry() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		log.append("acct-1", event("k3", 3));

		List<GeTradeEvent> entries = log.read("acct-1", 0, 50).getEntries();
		assertEquals(Long.valueOf(0), entries.get(0).getSeq());
		assertEquals(Long.valueOf(1), entries.get(1).getSeq());
		assertEquals(Long.valueOf(2), entries.get(2).getSeq());
	}

	@Test
	public void theOrdinalKeepsCountingAfterAReopen() {
		new FileFillLog(gson, dir).append("acct-1", event("k1", 1));
		FileFillLog reopened = new FileFillLog(gson, dir);
		reopened.append("acct-1", event("k2", 2));

		List<GeTradeEvent> entries = reopened.read("acct-1", 0, 50).getEntries();
		assertEquals(Long.valueOf(0), entries.get(0).getSeq());
		assertEquals(Long.valueOf(1), entries.get(1).getSeq());
	}

	@Test
	public void entriesSurviveReopening() {
		new FileFillLog(gson, dir).append("acct-1", event("k1", 3));
		List<GeTradeEvent> entries = new FileFillLog(gson, dir).read("acct-1", 0, 50).getEntries();
		assertEquals(1, entries.size());
		assertEquals(3, entries.get(0).getQuantity());
	}

	@Test
	public void markingDeliveredNeverRemovesALine() throws Exception {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		long everything = log.read("acct-1", 0, 50).getNextOffset();

		log.markDelivered("acct-1", everything);

		assertEquals(2, lineCount("acct-1"));
		assertTrue(logFile("acct-1").isFile());
		assertEquals(2, log.read("acct-1", 0, 50).getEntries().size());
		assertTrue(log.read("acct-1", everything, 50).getEntries().isEmpty());
	}

	@Test
	public void theDeliveredCursorSurvivesReopening() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		long afterFirst = log.read("acct-1", 0, 1).getNextOffset();
		log.markDelivered("acct-1", afterFirst);

		FileFillLog reopened = new FileFillLog(gson, dir);
		assertEquals(afterFirst, reopened.deliveredOffset("acct-1"));
		List<GeTradeEvent> rest = reopened.read("acct-1", reopened.deliveredOffset("acct-1"), 50).getEntries();
		assertEquals(1, rest.size());
		assertEquals("k2", rest.get(0).getIdempotencyKey());
	}

	@Test
	public void aMissingCursorStartsAtZero() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		assertEquals(0, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aCorruptCursorStartsAtZero() throws Exception {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		Files.write(new File(dir, "fills-acct-1.sent").toPath(), "not-a-number".getBytes(StandardCharsets.UTF_8));

		assertEquals(0, log.deliveredOffset("acct-1"));
		assertEquals(1, log.read("acct-1", log.deliveredOffset("acct-1"), 50).getEntries().size());
	}

	@Test
	public void aCursorPastTheEndOfTheLogClampsToTheLog() throws Exception {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		Files.write(new File(dir, "fills-acct-1.sent").toPath(), "999999".getBytes(StandardCharsets.UTF_8));

		assertEquals(logFile("acct-1").length(), log.deliveredOffset("acct-1"));
	}

	@Test
	public void theCursorNeverMovesBackwards() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		long everything = log.read("acct-1", 0, 50).getNextOffset();
		log.markDelivered("acct-1", everything);

		log.markDelivered("acct-1", 0);

		assertEquals(everything, log.deliveredOffset("acct-1"));
	}

	@Test
	public void readingFromAnOffsetReturnsOnlyWhatIsNew() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		FillBatch first = log.read("acct-1", 0, 1);
		assertEquals("k1", first.getEntries().get(0).getIdempotencyKey());

		FillBatch second = log.read("acct-1", first.getNextOffset(), 1);
		assertEquals(1, second.getEntries().size());
		assertEquals("k2", second.getEntries().get(0).getIdempotencyKey());
		assertTrue(log.read("acct-1", second.getNextOffset(), 50).getEntries().isEmpty());
	}

	@Test
	public void aTornLastWriteCannotBleedIntoTheNextEntry() throws Exception {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		Files.write(logFile("acct-1").toPath(), "{\"idempotency_key\":\"k2\",\"quan".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);

		FileFillLog reopened = new FileFillLog(gson, dir);
		reopened.append("acct-1", event("k3", 3));

		List<GeTradeEvent> entries = reopened.read("acct-1", 0, 50).getEntries();
		assertEquals(2, entries.size());
		assertEquals("k1", entries.get(0).getIdempotencyKey());
		assertEquals("k3", entries.get(1).getIdempotencyKey());
		assertEquals(3, entries.get(1).getQuantity());
	}

	@Test
	public void anUnparseableLineIsSkippedWithoutStallingTheCursor() throws Exception {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		Files.write(logFile("acct-1").toPath(), "{ not json\n".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
		log.append("acct-1", event("k3", 3));

		FillBatch batch = new FileFillLog(gson, dir).read("acct-1", 0, 50);
		assertEquals(2, batch.getEntries().size());
		assertEquals("k3", batch.getEntries().get(1).getIdempotencyKey());
		assertEquals(logFile("acct-1").length(), batch.getNextOffset());
	}

	@Test
	public void accountsAreIsolated() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("a", 1));
		log.append("acct-2", event("b", 2));

		assertEquals(1, log.read("acct-1", 0, 50).getEntries().size());
		assertEquals(1, log.read("acct-2", 0, 50).getEntries().size());
		assertEquals("a", log.read("acct-1", 0, 50).getEntries().get(0).getIdempotencyKey());

		log.markDelivered("acct-1", log.read("acct-1", 0, 50).getNextOffset());
		assertTrue(log.read("acct-1", log.deliveredOffset("acct-1"), 50).getEntries().isEmpty());
		assertEquals(0, log.deliveredOffset("acct-2"));
		assertEquals(1, log.read("acct-2", 0, 50).getEntries().size());
	}

	@Test
	public void unknownAccountAndNullsAreSafe() {
		FileFillLog log = new FileFillLog(gson, dir);
		assertTrue(log.read("nobody", 0, 50).getEntries().isEmpty());
		assertEquals(0, log.deliveredOffset("nobody"));
		log.append(null, event("k", 1));
		log.append("acct-1", null);
		log.markDelivered(null, 10);
		assertTrue(log.read("acct-1", 0, 50).getEntries().isEmpty());
		assertFalse(logFile("acct-1").isFile());
	}
}
