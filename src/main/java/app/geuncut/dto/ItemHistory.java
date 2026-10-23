package app.geuncut.dto;

import java.util.List;
import com.google.gson.annotations.SerializedName;
import lombok.Value;

/** The public item-detail candle contract shared with the website. Prices are GP. */
@Value
@lombok.AllArgsConstructor
public class ItemHistory {
	@SerializedName("intraday_candles")
	List<Candle> intraday;
	@SerializedName("daily_candles")
	List<Candle> daily;
	ItemFacts item;

	public ItemHistory(List<Candle> intraday, List<Candle> daily) {
		this(intraday, daily, null);
	}

	@Value
	public static class ItemFacts {
		@SerializedName("buy_limit")
		Long buyLimit;
		Boolean members;
		@SerializedName("high_alch")
		Long highAlch;
		@SerializedName("low_alch")
		Long lowAlch;
	}

	@Value
	@lombok.AllArgsConstructor
	public static class Candle {
		@SerializedName("bucket_ts")
		String bucketTs;
		String day;
		@SerializedName("avg_low_price")
		Long buy;
		@SerializedName("avg_high_price")
		Long sell;
		@SerializedName("low_price_volume")
		Long buyVolume;
		@SerializedName("high_price_volume")
		Long sellVolume;

		public Candle(String bucketTs, String day, Long buy, Long sell) {
			this(bucketTs, day, buy, sell, null, null);
		}
	}
}
