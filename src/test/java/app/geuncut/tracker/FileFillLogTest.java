package app.geuncut.tracker;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import app.geuncut.dto.GeTradeEvent;
import app.geuncut.tracker.impl.FileFillLog;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonList;
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

	@Test
	public void appendThenPendingRoundTripsTheFill() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 7));

		List<GeTradeEvent> pending = log.pending("acct-1");
		assertEquals(1, pending.size());
		assertEquals("k1", pending.get(0).getIdempotencyKey());
		assertEquals(7, pending.get(0).getQuantity());
		assertEquals("buy", pending.get(0).getSide());
	}

	@Test
	public void unackedFillsSurviveReopening() {
		new FileFillLog(gson, dir).append("acct-1", event("k1", 3));
		List<GeTradeEvent> pending = new FileFillLog(gson, dir).pending("acct-1");
		assertEquals(1, pending.size());
		assertEquals(3, pending.get(0).getQuantity());
	}

	@Test
	public void ackRemovesOnlyTheAckedFills() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.append("acct-1", event("k2", 2));
		log.append("acct-1", event("k3", 3));

		log.ack("acct-1", singletonList("k2"));

		List<GeTradeEvent> pending = log.pending("acct-1");
		assertEquals(2, pending.size());
		assertEquals("k1", pending.get(0).getIdempotencyKey());
		assertEquals("k3", pending.get(1).getIdempotencyKey());
	}

	@Test
	public void ackingEverythingDeletesTheFile() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 1));
		log.ack("acct-1", singletonList("k1"));

		assertTrue(log.pending("acct-1").isEmpty());
		assertFalse(new File(dir, "fills-acct-1.jsonl").exists());
	}

	@Test
	public void aDoubleAppendOfTheSameKeyCollapsesToOne() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("k1", 5));
		log.append("acct-1", event("k1", 5));

		assertEquals(1, log.pending("acct-1").size());
	}

	@Test
	public void accountsAreIsolated() {
		FileFillLog log = new FileFillLog(gson, dir);
		log.append("acct-1", event("a", 1));
		log.append("acct-2", event("b", 2));

		assertEquals(1, log.pending("acct-1").size());
		assertEquals(1, log.pending("acct-2").size());
		assertEquals("a", log.pending("acct-1").get(0).getIdempotencyKey());

		log.ack("acct-1", singletonList("a"));
		assertTrue(log.pending("acct-1").isEmpty());
		assertEquals(1, log.pending("acct-2").size());
	}

	@Test
	public void unknownAccountAndNullsAreSafe() {
		FileFillLog log = new FileFillLog(gson, dir);
		assertTrue(log.pending("nobody").isEmpty());
		log.append(null, event("k", 1));
		log.append("acct-1", null);
		log.ack("acct-1", singletonList("missing"));
		assertTrue(log.pending("acct-1").isEmpty());
	}
}
