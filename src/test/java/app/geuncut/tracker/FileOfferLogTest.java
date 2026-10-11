package app.geuncut.tracker;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import app.geuncut.dto.OfferState;
import app.geuncut.tracker.impl.FileOfferLog;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileOfferLogTest {
	private File dir;
	private Gson gson;

	@Before
	public void setUp() throws Exception {
		dir = Files.createTempDirectory("offerlog").toFile();
		gson = new Gson();
	}

	@After
	public void tearDown() {
		for (File f : dir.listFiles() != null ? dir.listFiles() : new File[0]) {
			f.delete();
		}
		dir.delete();
	}

	private static OfferState state(int filled) {
		return OfferState.builder()
				.installId("install-1").slot(0).state("buying").itemId(20997).side("buy")
				.priceEach(1000).quantityTotal(100).quantityFilled(filled).spent(filled * 1000L)
				.observedAt("2026-07-05T12:00:00Z").build();
	}

	private File logFile(String accountHash) {
		return new File(dir, "offers-" + accountHash + ".jsonl");
	}

	private int lineCount(String accountHash) throws Exception {
		File file = logFile(accountHash);
		if (!file.isFile()) {
			return 0;
		}
		return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).size();
	}

	@Test
	public void appendThenReadRoundTripsTheState() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(7));

		OfferBatch batch = log.read("acct-1", 0, 50);
		assertEquals(1, batch.getEntries().size());
		OfferState read = batch.getEntries().get(0);
		assertEquals("buying", read.getState());
		assertEquals(7, read.getQuantityFilled());
		assertEquals(7000L, read.getSpent());
		assertEquals("buy", read.getSide());
		assertEquals("install-1", read.getInstallId());
		assertEquals("2026-07-05T12:00:00Z", read.getObservedAt());
		assertEquals(logFile("acct-1").length(), batch.getNextOffset());
	}

	@Test
	public void theFilesAreNamedForOfferStatesAndLeaveOldFillLogsAlone() throws Exception {
		File oldFills = new File(dir, "fills-acct-1.jsonl");
		Files.write(oldFills.toPath(), "{\"idempotency_key\":\"k\"}\n".getBytes(StandardCharsets.UTF_8));
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.markDelivered("acct-1", log.read("acct-1", 0, 50).getNextOffset());

		assertTrue(logFile("acct-1").isFile());
		assertTrue(new File(dir, "offers-acct-1.sent").isFile());
		assertEquals("{\"idempotency_key\":\"k\"}\n",
				new String(Files.readAllBytes(oldFills.toPath()), StandardCharsets.UTF_8));
		assertEquals(1, log.read("acct-1", 0, 50).getEntries().size());
	}

	@Test
	public void anEmptySlotRoundTripsWithoutASide() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", OfferState.builder().slot(3).state("empty").observedAt("2026-07-05T12:00:00Z").build());

		OfferState read = log.read("acct-1", 0, 50).getEntries().get(0);
		assertEquals("empty", read.getState());
		assertEquals(3, read.getSlot());
		assertEquals(0, read.getItemId());
		assertNull(read.getSide());
	}

	@Test
	public void appendStampsTheOrdinalOfEveryEntry() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-1", state(2));
		log.append("acct-1", state(3));

		List<OfferState> entries = log.read("acct-1", 0, 50).getEntries();
		assertEquals(Long.valueOf(0), entries.get(0).getSeq());
		assertEquals(Long.valueOf(1), entries.get(1).getSeq());
		assertEquals(Long.valueOf(2), entries.get(2).getSeq());
	}

	@Test
	public void theOrdinalKeepsCountingAfterAReopen() {
		new FileOfferLog(gson, dir).append("acct-1", state(1));
		FileOfferLog reopened = new FileOfferLog(gson, dir);
		reopened.append("acct-1", state(2));

		List<OfferState> entries = reopened.read("acct-1", 0, 50).getEntries();
		assertEquals(Long.valueOf(0), entries.get(0).getSeq());
		assertEquals(Long.valueOf(1), entries.get(1).getSeq());
	}

	@Test
	public void entriesSurviveReopening() {
		new FileOfferLog(gson, dir).append("acct-1", state(3));
		List<OfferState> entries = new FileOfferLog(gson, dir).read("acct-1", 0, 50).getEntries();
		assertEquals(1, entries.size());
		assertEquals(3, entries.get(0).getQuantityFilled());
	}

	@Test
	public void markingDeliveredNeverRemovesALine() throws Exception {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-1", state(2));
		long everything = log.read("acct-1", 0, 50).getNextOffset();

		log.markDelivered("acct-1", everything);

		assertEquals(2, lineCount("acct-1"));
		assertEquals(2, log.read("acct-1", 0, 50).getEntries().size());
		assertTrue(log.read("acct-1", everything, 50).getEntries().isEmpty());
	}

	@Test
	public void theDeliveredCursorSurvivesReopening() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-1", state(2));
		long afterFirst = log.read("acct-1", 0, 1).getNextOffset();
		log.markDelivered("acct-1", afterFirst);

		FileOfferLog reopened = new FileOfferLog(gson, dir);
		assertEquals(afterFirst, reopened.deliveredOffset("acct-1"));
		List<OfferState> rest = reopened.read("acct-1", reopened.deliveredOffset("acct-1"), 50).getEntries();
		assertEquals(1, rest.size());
		assertEquals(2, rest.get(0).getQuantityFilled());
	}

	@Test
	public void aMissingCursorStartsAtZero() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		assertEquals(0, log.deliveredOffset("acct-1"));
	}

	@Test
	public void aCorruptCursorStartsAtZero() throws Exception {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		Files.write(new File(dir, "offers-acct-1.sent").toPath(), "not-a-number".getBytes(StandardCharsets.UTF_8));

		assertEquals(0, log.deliveredOffset("acct-1"));
		assertEquals(1, log.read("acct-1", log.deliveredOffset("acct-1"), 50).getEntries().size());
	}

	@Test
	public void aCursorPastTheEndOfTheLogClampsToTheLog() throws Exception {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		Files.write(new File(dir, "offers-acct-1.sent").toPath(), "999999".getBytes(StandardCharsets.UTF_8));

		assertEquals(logFile("acct-1").length(), log.deliveredOffset("acct-1"));
	}

	@Test
	public void theCursorNeverMovesBackwards() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-1", state(2));
		long everything = log.read("acct-1", 0, 50).getNextOffset();
		log.markDelivered("acct-1", everything);

		log.markDelivered("acct-1", 0);

		assertEquals(everything, log.deliveredOffset("acct-1"));
	}

	@Test
	public void readingFromAnOffsetReturnsOnlyWhatIsNew() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-1", state(2));
		OfferBatch first = log.read("acct-1", 0, 1);
		assertEquals(1, first.getEntries().get(0).getQuantityFilled());

		OfferBatch second = log.read("acct-1", first.getNextOffset(), 1);
		assertEquals(1, second.getEntries().size());
		assertEquals(2, second.getEntries().get(0).getQuantityFilled());
		assertTrue(log.read("acct-1", second.getNextOffset(), 50).getEntries().isEmpty());
	}

	@Test
	public void aTornLastWriteCannotBleedIntoTheNextEntry() throws Exception {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		Files.write(logFile("acct-1").toPath(), "{\"state\":\"buying\",\"quan".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);

		FileOfferLog reopened = new FileOfferLog(gson, dir);
		reopened.append("acct-1", state(3));

		List<OfferState> entries = reopened.read("acct-1", 0, 50).getEntries();
		assertEquals(2, entries.size());
		assertEquals(1, entries.get(0).getQuantityFilled());
		assertEquals(3, entries.get(1).getQuantityFilled());
	}

	@Test
	public void anUnparseableLineIsSkippedWithoutStallingTheCursor() throws Exception {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		Files.write(logFile("acct-1").toPath(), "{ not json\n".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
		log.append("acct-1", state(3));

		OfferBatch batch = new FileOfferLog(gson, dir).read("acct-1", 0, 50);
		assertEquals(2, batch.getEntries().size());
		assertEquals(3, batch.getEntries().get(1).getQuantityFilled());
		assertEquals(logFile("acct-1").length(), batch.getNextOffset());
	}

	@Test
	public void accountsAreIsolated() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(1));
		log.append("acct-2", state(2));

		assertEquals(1, log.read("acct-1", 0, 50).getEntries().size());
		assertEquals(1, log.read("acct-2", 0, 50).getEntries().size());
		assertEquals(1, log.read("acct-1", 0, 50).getEntries().get(0).getQuantityFilled());

		log.markDelivered("acct-1", log.read("acct-1", 0, 50).getNextOffset());
		assertTrue(log.read("acct-1", log.deliveredOffset("acct-1"), 50).getEntries().isEmpty());
		assertEquals(0, log.deliveredOffset("acct-2"));
		assertEquals(1, log.read("acct-2", 0, 50).getEntries().size());
	}

	@Test
	public void unknownAccountAndNullsAreSafe() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		assertTrue(log.read("nobody", 0, 50).getEntries().isEmpty());
		assertEquals(0, log.deliveredOffset("nobody"));
		log.append(null, state(1));
		log.append("acct-1", null);
		log.append("acct-1", OfferState.builder().slot(0).build());
		log.markDelivered(null, 10);
		assertTrue(log.read("acct-1", 0, 50).getEntries().isEmpty());
		assertFalse(logFile("acct-1").isFile());
	}

	@Test
	public void largePricesAndSpendSurviveReplay() {
		FileOfferLog log = new FileOfferLog(gson, dir);
		log.append("acct-1", state(2).toBuilder().priceEach(3_000_000_001L).spent(6_000_000_002L).build());

		OfferState replay = new FileOfferLog(gson, dir).read("acct-1", 0, 50).getEntries().get(0);
		assertEquals(3_000_000_001L, replay.getPriceEach());
		assertEquals(6_000_000_002L, replay.getSpent());
		assertTrue(gson.toJson(replay).contains("\"price_each\":3000000001"));
	}
}
