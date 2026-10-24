# In-client item price history

`OfferPriceOverlay` shows a compact summary when a player selects an item from
GE search and its setup appears. Opening the price-entry prompt is not required.
The summary retains the item sprite, Buy/Sell prices, quote ages, and separate
volume counts. One full-width **Show chart** button reveals history below the
unchanged summary; **Hide chart** hides only that section. There is no Details
tab or website button in the popup.

**Show GE item details** controls the whole popup and defaults to enabled. It
retains the `showItemChart` configuration key and saved preference. Changes apply
on the client thread immediately; disabling clears the popup and pending history
generations. Re-enabling starts collapsed with a fresh request. **Show GE price
tips** independently controls suggestions in the price prompt. The header **X**
dismisses the current selection through refreshes and late responses. A new
item, reopening setup, or disabling/re-enabling restores the collapsed summary.

## Data and meaning

`HttpGeUncutApi.fetchItemHistory` uses the public `GET /api/market/items/{itemId}`
endpoint shared with the website. Fields are nullable 64-bit bucket averages
and reported quantities, not OHLC or guaranteed instant-fill quotes. No backend
changes or account linking are needed.

- **24H** uses `intraday_candles`, five-minute `bucket_ts` observations.
- **1W**, **1M** (default), and **1Y** use `daily_candles`, source `day` dates.
- Player Buy maps to `avg_low_price` / `low_price_volume`; Sell maps to
  `avg_high_price` / `high_price_volume`. This matches the app's existing
  `buy_price` / `latest_low` and `sell_price` / `latest_high` conventions.
  Low/high denote trade sides, not price extrema.
- Top volume uses the latest reported bucket in the selected range, not a
  full-range total. Missing periods and sides are never added or inferred.
- Intraday timestamps use the OS/JVM timezone, including date rollover and DST.
  Hover includes the numeric offset to distinguish repeated local hours.
  API instants are unchanged. Daily labels keep their UTC source date rather
  than implying local-day aggregation.
- Missing volume is **Not reported**, distinct from **0**. Missing prices are
  **n/a**. Zero volume has a baseline mark; missing volume has no bar.
- Windows end at the newest observation, matching the website. Intraday data
  over one hour old or daily data over two days old is marked **Older data**.
  Sparse observations leave gaps; there are no synthetic prices or forecasts.

Hovering the price plot or volume bars selects a real bucket. A compact table
shows its timestamp, exact Buy/Sell GP prices and separate item counts. The
ordinary tooltip is 65 logical pixels tall; long values use a 73-pixel stacked
layout to retain every digit. It appears opposite the hovered plot. The chart's
daily/five-minute averages label supplies bucket context without repeating a
tooltip explanation row.

## Integration and rendering

Selection uses `ScriptID.GE_OFFERS_SETUP_BUILD`, `CURRENT_GE_ITEM` (varp 1151),
visible GE window/setup widgets, and a hidden search-results list. The cached
RuneLite Item Stats integration uses this script/varp pair. Post-client-tick and
widget-close checks clear selections on Back, Close, search, logout, hopping or
disconnect. Closing just the price prompt preserves the selected-item popup.
This lifecycle does not write game variables or offer values.

`ItemHistoryService` tracks one active item, deduplicates repeated ticks,
refreshes successful data after 60 seconds, and guards callbacks with generations
across A/B/A searches and close/reopen. Errors wait for **Retry** or a new
selection. Loading, empty and error states retain the header.

The chart renders native antialiased Java2D text, lines and volume bars. The
summary reuses the production popup's small font, six-pixel padding and 36x32
sprite column. Width follows content with a 232-pixel minimum. The full-width
chart control uses 12-point bold text and a 28-pixel hit area: usual collapsed
height is 104 pixels and expanded height 294. Width and summary stay unchanged
when expanded. The background retains 220/255 opacity; text and plots are
opaque. All controls have matching logical paint/hit bounds and visible hover
and active states. The sprite uses `ItemManager.getImage` and stays associated
with its selection. Time tick spacing and exact hover values fit available space.

## Verification

Run `gradlew.bat build --no-daemon` with Java 17 (Java release target 11).
There is no separate lint task. Do not use local login helpers to run tests;
they may modify launcher settings.

Tests cover HTTP parsing, side mapping, missing/zero/large values, all ranges,
local midnight and DST, daily source dates, stale responses, repeated selections,
retries, display without a price prompt, close/reset, master toggle, X, volume
retention, hidden hit targets, and native layouts at 100%/125% scale. Headless
renders exercise the actual component; live placement and stretched-mode input
still require user testing.
