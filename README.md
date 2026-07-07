# GE Uncut

A live flip finder for the Grand Exchange, in a RuneLite side panel. It surfaces
flips from [geuncut.app](https://geuncut.app), tracks your realized profit from
your own GE offers, and shows rolling buy-limit timers.

## Features

- **Flip finder** — a ranked list of flips (buy price, quantity, sell target,
  after-tax margin, ROI/day, demand trend, and realistic fill times), refreshed
  on demand. Works without an account: non-members (F2P) flips are free; linking
  an account adds members-item flips.
- **Working offers** — a live mirror of your current GE slots with fill progress,
  tracked entirely on your own client.
- **Profit tracking** — when you link an account, your own offer fills are
  reported so flips track themselves in "My Flips" on geuncut.app.
- **Buy-limit timers** — a rolling 4-hour per-item buy-limit counter, kept
  client-side.

## Linking an account (optional)

The plugin is fully usable without an account. Linking is only needed for
members-item flips and cross-device profit tracking. It uses a pairing code:
click **Link account** in the panel, enter the short code shown at
`geuncut.app/link`, and a personal token is delivered and stored automatically.
The plugin never sees your RuneScape or geuncut.app password. Click **Unlink**
in the panel header to disconnect and clear the token.

## Data & privacy

**Nothing is sent to any server until you link an account.** Unlinked, the plugin
only fetches the public flip list (the same anonymous scan the geuncut.app
website serves) and does its buy-limit and working-offers tracking locally.

Once linked, the plugin talks to `geuncut.app` only, over HTTPS, using your
personal token. It sends:

- **Your OSRS account hash** — the client's non-reversible account identifier,
  used to attribute your offers and fills to your account. Not your username.
- **Your GE offer fills** — item id, quantity, price, side, slot, and timestamp
  of each of your own trades, so your flips track themselves in My Flips.
- **A snapshot of your current GE offers** — the live slot state (item, prices,
  fill progress) for the working-offers view on the website.

The exact endpoints, all under `https://geuncut.app/api/plugin/`, are: `flips`
(the scan; anonymous or personalized), `positions` (your open flips), `ge-events`
(your fills), `offers` (your current slot snapshot), and `link/start` + `link/poll`
(pairing). No credentials are ever transmitted, and no data leaves the client
while unlinked.

## Building

Standard RuneLite external plugin. `./gradlew build` to compile and test;
`./gradlew run` launches a developer-mode client with the plugin loaded.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
