package app.geuncut.dto;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoinSerializationTest {
    private final Gson gson = new Gson();

    @Test
    public void offersAndPlacementsSerializeLargePricesAsJsonNumbers() {
        GeOffer offer = GeOffer.builder().itemId(20997).priceEach(3_000_000_001L).build();
        String json = gson.toJson(offer);
        assertTrue(json.contains("\"price_each\":3000000001"));
        assertEquals(3_000_000_001L, gson.fromJson(json, GeOffer.class).getPriceEach());
        assertEquals(3_000_000_001L, gson.fromJson("{\"price_each\":3000000001}", OfferPlacement.class).getPriceEach());
        assertEquals(1000L, gson.fromJson("{\"price_each\":1000}", GeOffer.class).getPriceEach());
    }
}
