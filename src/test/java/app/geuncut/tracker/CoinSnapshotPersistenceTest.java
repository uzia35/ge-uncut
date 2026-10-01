package app.geuncut.tracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import app.geuncut.config.GeUncutConfig;
import app.geuncut.model.OfferDelta;
import app.geuncut.tracker.impl.ConfigSnapshotStore;
import app.geuncut.tracker.impl.OfferTrackerImpl;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static net.runelite.api.GrandExchangeOfferState.BUYING;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CoinSnapshotPersistenceTest {
    @Test
    public void configJsonReplaysLargeBaselineAndPreservesLegacyOfferIdentity() {
        ConfigManager config = mock(ConfigManager.class);
        AtomicReference<String> json = new AtomicReference<>(
            "{\"acct\":{\"0\":{\"itemId\":20997,\"quantitySold\":1,\"spent\":1000,\"state\":\"BUYING\",\"quantityTotal\":3,\"price\":1000,\"offerId\":\"legacy\"}}}");
        when(config.getConfiguration(GeUncutConfig.GROUP, "offerBaselines")).thenAnswer(call -> json.get());
        doAnswer(call -> { json.set(call.getArgument(2)); return null; })
            .when(config).setConfiguration(eq(GeUncutConfig.GROUP), eq("offerBaselines"), any());
        OfferTracker tracker = new OfferTrackerImpl(new ConfigSnapshotStore(config, new Gson()));
        tracker.loadFor("acct");
        List<OfferDelta> fills = new ArrayList<>();
        tracker.onOfferChanged(0, 20997, BUYING, 2, 3_000_001_001L, 3, 3_000_000_001L, Instant.EPOCH, fills::add);
        assertEquals(3_000_000_001L, fills.get(0).getPriceEach());
        assertEquals("legacy", fills.get(0).getOfferId());
        assertTrue(json.get().contains("\"spent\":3000001001"));
        OfferTracker reopened = new OfferTrackerImpl(new ConfigSnapshotStore(config, new Gson()));
        reopened.loadFor("acct");
        fills.clear();
        reopened.onOfferChanged(0, 20997, BUYING, 2, 3_000_001_001L, 3, 3_000_000_001L, Instant.EPOCH, fills::add);
        assertTrue(fills.isEmpty());
        reopened.onOfferChanged(0, 20997, BUYING, 3, 6_000_001_002L, 3, 3_000_000_001L, Instant.EPOCH, fills::add);
        assertEquals(3_000_000_001L, fills.get(0).getPriceEach());
        assertEquals("legacy", fills.get(0).getOfferId());
    }
}
