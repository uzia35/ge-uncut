# In-client item price history

The existing `OfferPriceOverlay` shows a compact summary as soon as the player
selects an item from GE search and its item setup appears. **Show chart** reveals
historical Buy/Sell averages below that summary as an accordion.
Opening the price-entry prompt is not required. **Show GE item details** controls
the entire popup and is enabled by default. Turning it off hides the summary,
chart and Details view, even while the price-entry prompt is open. **Show GE price
tips** independently controls clickable suggestions inside that prompt. The
**Item details** button opens volume details inside the selected-item card;
**Open website** remains available there. No account linking is needed.
The existing saved `showItemChart` configuration key is retained, including its
enabled/disabled preference. Configuration changes apply on the client thread
without waiting for a server tick; disabling clears pending history generations
and overlay controls. Re-enabling shows the selected item collapsed with a fresh
history request. The in-popup Show/Hide chart accordion remains separate.
The small **×** in the header dismisses the entire popup for the current item
selection. Routine refreshes and late data cannot reopen it. A different item,
leaving and reopening setup, or disabling/re-enabling item details restores the
default collapsed summary. Closing the popup does not change the saved setting.

- **24H** uses the public item's five-minute candles; **1W**, **1M** (default),
  and **1Y** use its daily candles, matching the website's range labels.
- All price surfaces use **Buy** and **Sell**, from the player's
  buy-offer/sell-offer perspective. This preserves the app's existing mapping:
  API `buy_price` / `latest_low` / `avg_low_price` is Buy; `sell_price` /
  `latest_high` / `avg_high_price` is Sell. The underlying low/high fields are
  trade sides, not minimum/maximum prices. They are not guaranteed instant-fill
  quotes. The chart explicitly labels
  five-minute or daily averages.
- The default collapsed view contains item identity, two recorded prices and
  their ages (`low_time` / `high_time` through the original `PriceHint.ago`), plus
  always-visible Buy/Sell volume counts in a **Volume** column beside the
  original stacked Buy/Sell price-and-age rows.
  The collapsed view has no graph, histogram, axes, range controls or hidden
  chart click targets. **Show chart** reveals the chart with GP/time axes,
  aligned Buy/Sell volume bars and range controls beneath the unchanged summary.
  **Hide chart** hides only that section; the selected range is retained. Buy/Sell
  colors apply to both price lines (GP) and separate volume bars (items).
  Zero volume has a baseline mark; missing volume has no bar. The time axis is
  unchanged. It omits change percentages, membership, buy-limit and alchemy rows.
  The top volume summary uses the latest reported bucket in the selected range.
  Its date/time line is intentionally omitted; full bucket-period and partial
  coverage information remains in Details and point hover. These are not 24-hour
  or full-range totals. Loading and unavailable states are explicit; zero remains
  **0** and missing volume **Not reported**. Quantities remain item counts, and
  the content-sized summary retains exact integers. The chart histogram also
  uses the concise **Volume** label; the line/bar unit explanation is omitted.
  **Item details** shows separate reported Buy/Sell volumes in items for the latest
  bucket in the selected range. **24H** uses a five-minute bucket with its local
  start timestamp and duration; daily ranges show the source UTC date and
  00:00–24:00 UTC boundaries. Current buckets are marked **In progress**.
  These are reported bucket counts, explicitly not full-range totals; no missing
  periods or sides are added or inferred. Zero is **0 items**, missing/invalid
  volume is **Not reported**. **Close details** restores the prior collapsed or
  expanded state and keeps the range; **Open website**
  provides secondary item facts.
- Hover over a real observation to snap the crosshair and highlighted dots to
  its bucket. A compact card shows the timestamp (date for a daily bucket),
  averaging period and exact Buy price and Sell price in GP. The same point card includes Buy/Sell volume counts
  in items below the GP prices, whether hovering the price plot or volume bars.
  Volume bars and the separate Details view remain available. Missing prices remain `n/a` and
  missing volume is **Not reported**. There are no synthetic candles or
  inferred OHLC values.
- Intraday axes and tooltips use the player's OS/JVM timezone. Axis labels show
  the zone abbreviation; tooltips also show the numeric UTC offset so repeated
  clock times at the end of daylight saving remain distinguishable. API instants
  and the 24-hour window are unchanged. Daily ranges retain their source UTC date;
  they are not converted to local-day averages. Chart and hover annotations omit
  the redundant **UTC** and **partial** wording. Details retains the full source
  period and in-progress coverage. Intraday hover retains its actual local clock
  time, zone abbreviation and numeric offset, without an extra UTC prefix.
- The header uses the selected item's existing RuneLite `ItemManager.getImage`
  sprite. Its asynchronous image buffer stays associated with that item; changing
  or clearing selection cannot replace the current icon with an old load.
- Windows end at the newest recorded observation, matching the website. An
  older intraday observation (over one hour) or daily observation (over two days)
  is labelled `Older data` beside the averaging period. Missing sides remain missing and missing buckets
  produce gaps; no synthetic prices or forecasts are added.
- Loading, empty, and error states retain the item header. **Retry**
  retries a failed request. Changing/deselecting the item, returning to the GE
  overview or search list, closing the GE window, logging out, hopping,
  disconnecting, or disabling item details/the plugin invalidates pending chart work.
  Closing just the price prompt preserves the selected item's chart.

## Data and integration

`HttpGeUncutApi.fetchItemHistory` reads `GET /api/market/items/{itemId}`, the same
public endpoint as the website item page. `ItemHistory` consumes
`intraday_candles` (`bucket_ts`) and `daily_candles` (`day`), with
`avg_low_price` mapped to Buy and `avg_high_price` to Sell. Prices use nullable
64-bit integers, as do volume quantities. The optional `item` object supplies
`members`, `buy_limit`, `high_alch`, and `low_alch`. The API provides bucket averages, not
OHLC; no candlesticks are invented. Missing candle arrays are an API error, while empty arrays are
a valid empty result. No backend changes are required.

`ItemHistoryService` keeps one active item's request state, deduplicates repeated
game ticks, refreshes successful results after 60 seconds, and guards callbacks
with request generations so an old A request cannot replace a new A/B/A search.
Errors wait for explicit retry or a new selection. `GeUncutPlugin` supplies the
selected item, routes chart clicks, and clears state on host lifecycle changes.
Selection uses `ScriptID.GE_OFFERS_SETUP_BUILD` and `CURRENT_GE_ITEM` (varp 1151,
also named `TRADINGPOST_SEARCH`). The cached RuneLite 1.13.1 Item Stats plugin
uses this same script/varp pair for its GE item information. Post-client-tick
checks require visible `GeOffers.UNIVERSE` and `GeOffers.SETUP` widgets and no
visible GE search-results list; this prevents retained varp values from showing
an old item after Back/Close. There is one chart for the selected item, never
a chart for each search-list row. No game variables or offer values are written
by the chart selection lifecycle.
`ItemHistoryChart` renders natively with Java2D; the website's React chart cannot
be embedded in RuneLite's overlay renderer. The chart uses an antialiased
SansSerif font and vector lines at the host graphics scale, not a resized chart
bitmap. The summary reuses the tracked production popup's RuneScape Small font,
6-pixel padding, 36×32 item sprite column, 12-pixel line height and stacked rows.
For the captured Dragon bones data, production measures 161×97 logical pixels;
the summary with the added volume column is 232×97. Width follows measured content,
with a 232-pixel minimum to keep cheap, short-name items readable. The smaller chart/details controls share the original
21-pixel button row. **Show chart** adds the graph below the same summary,
producing 232×287 for that data; width and summary stay unchanged on toggling.
**Hide chart** returns to the summary without losing
the selected range. A new item, closing selection or disabling item details resets
to collapsed. Details can be opened independently from either state and use the
same body area. Both views fit within the fixed viewport's 334-pixel height.
The backdrop has 220/255 opacity (about 86%); text, curves and bars stay opaque.
Details, Show/Hide chart, range and website controls share visible button fills,
borders, hover highlights and active states, with matching logical hit bounds.
Time ticks adapt to their measured label widths. Exact historical values live in a
bounded hover card whose font fits long quantities without dropping digits; the averaging
period, time axis, legend and range controls stay visible in the expanded section. Narrow billion-GP
ranges use distinct exact axis labels when abbreviation would hide differences.

## Verification

Run `gradlew.bat build --no-daemon` with Java 17 configured (Java release target
11). Do not use the local login helper merely to run tests; it may update RuneLite
launcher settings. Gradle compiles production/test sources and runs JUnit; this
repository has no separate lint task configured.

Tests cover the HTTP contract and item facts, large/missing prices and quantities,
UTC windows, local date rollover, DST gaps/repeated clock times, non-hour offsets,
daily UTC-date preservation, sparse data, range changes, hover bucket selection, distinct axis
ticks, native viewport fit, zero/unknown volume,
stale and repeated requests, retries, selection without a price prompt, deselect,
Back/Close/search-list transitions, independent chart/tip settings, and
moved-overlay clicks, and delayed item sprites across selection changes.
Volume details tests cover all ranges, separate API sides, zero/missing values,
partial buckets, local/UTC period semantics, local navigation, loading/retry,
selection changes, dismissal through the close control, and disabling item details.
Collapsed/expanded checks cover absent chart targets, unchanged summary pixels,
recorded price ages, same-width dimensions, range/reset behavior, 125% Java2D scaling,
translucent backdrop pixels, button hover/active pixels, and missing versus zero
volume rasterization. Headless tests do not establish live client placement.
`OfferPriceOverlayChartTest.main` can render a captured public item-detail
JSON file using the test runtime classpath; arguments are the input file and an
output directory. Rendered previews exercise the actual Java2D component, but
in-game placement and interaction still need a manual RuneLite check.
