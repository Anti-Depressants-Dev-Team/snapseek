# SnapSeek

Browse Pinterest, Pixiv, Safebooru, DeviantArt and more inside one window, and save any image in the format you want.

> **This branch is the Kotlin rebuild.** The shipping Electron app lives under [`legacy-electron/`](legacy-electron/) until the rebuild reaches parity. The design and phased plan are in the proposal document linked from the pull request.

## What's here

| Module | Role |
|---|---|
| `core/` | Pure Kotlin. Settings, services, the download pipeline (resolve → fetch → hash → transcode → name → write → record), site rules, ad-block list. No UI, no Chromium. Unit-tested. |
| `browser/` | Chromium through [JCEF](https://github.com/jcefmaven/jcefmaven). Request interception (Referer, ad blocking), image context menu, page scripts (Dark Reader, lazy-image fixes, Alt+click), cookie access. Everything behind a small `BrowserEngine` interface. |
| `app/` | Compose Multiplatform desktop UI: frameless window, service grid, browser screen with download tray, settings, history. Packaging via `jpackage`. |

## Build and run

Requires JDK 21. Gradle downloads itself through the wrapper.

```bash
./gradlew :app:run
```

First launch downloads the Chromium runtime (about 100 MB) into the app's cache directory and shows progress on the home screen. Later launches start in a second or two.

Run the tests:

```bash
./gradlew test
```

Build an installer for the current OS (Windows needs the WiX Toolset 3.x on the PATH; CI has it):

```bash
./gradlew :app:packageMsi
```

## Using it

Every site on the home screen opens as a native grid: a search box, previews, one-click saving, bookmarks, bulk download. The old embedded browser is one click away on each card ("Open website instead") and is still where you log in.

**Platforms with native browsing**

| Platform | How | Needs |
|---|---|---|
| Pinterest | Pinterest's own web search endpoint, paged by bookmark token, originals + 236/474/736 renders | nothing |
| Pixiv | AJAX tag search, daily ranking when the search is empty, tag suggestions | nothing; log in on the website tab for R-18 |
| DeviantArt | public RSS feed: `boost:popular`, `by:artist`, `in:digitalart` | nothing; 800px previews |
| Wallhaven | public API, `sorting:toplist`, `atleast:2560x1440` | key only for NSFW |
| Zerochan | JSON listing, comma-separated tags, suggestions | nothing |
| Giphy, Tenor | official APIs | a free key each (developers.giphy.com, Google Cloud) |
| Unsplash, Pexels, Pixabay | official APIs | a free key each |
| Any other site | "Image grid from a page": give a URL with `{q}` where the search goes and it lists the images on that page (Wallpapers.com ships this way) | nothing |

Reddit is missing on purpose: it no longer answers anonymous JSON requests.

**Your Pinterest account.** Open Pinterest and it checks the embedded browser's session straight away: if you are already signed in, the account chip shows your name and the grid opens on your **home feed**, with no extra steps. If not, press **Connect account**, sign in on the page that opens, and the app brings you back the moment the session appears, feed and boards loaded. There is a **Back to Pinterest** button on that page too, for when you would rather return yourself.

Once connected, the account chip lists **your boards** (browse any of them in the grid) and every pin offers **Save to board** from the details panel, the multi-select bar, or `P` for your last board. New boards can be created from the same menu. Nothing is stored by SnapSeek beyond the browser's own cookies; log out on the website tab to disconnect.

**Boorus**

Borrowed from [Boorusama](https://github.com/khoadng/Boorusama): boorus are browsed natively through their API instead of a web page. Six API families are supported, which covers most sites out there:

| Family | Sites | Notes |
|---|---|---|
| Gelbooru 0.2 | safebooru.org (built in), gelbooru.com, rule34.xxx, tbib.org, xbooru.com, hypnohub.net, realbooru.com | gelbooru.com wants an API key + user ID |
| Danbooru | danbooru.donmai.us, safebooru.donmai.us (built in), aibooru.online | anonymous searches take two tags |
| Moebooru | yande.re, konachan.com, konachan.net | |
| e621 | e621.net, e926.net | |
| Philomena | derpibooru.org, ponybooru.org, furbooru.org | comma-separated tags |
| Szurubooru | self-hosted instances | username + token if the instance requires it |

Home → Manage services → **Add a site** lists all of the above as one-click presets. **Add by URL** takes any URL and has a **Detect** button that recognises known hosts and asks unknown ones which API they speak. Accounts and API keys are entered per site with the key icon.

**Website mode** (the embedded Chromium) is still there for anything else: right-click an image for Save as PNG / JPEG / WebP / GIF / Original, or Alt+click to save in your default format. Pinterest thumbnails and Pixiv renders are upgraded to originals on the way out.

- Tag search with autocomplete, `-tag` exclusion, each site's metatags, and a recent-searches row.
- Masonry grid with infinite scroll. Hover a post to save, bookmark or select it. Select several and save them together.
- **Download everything** matching a search: choose a cap, quality, and whether to put the files in a folder named after the search. Blacklisted posts are skipped; posts you already saved are recognised by hash without re-downloading.
- Post details: full-size preview, tags grouped and coloured by category (artist, copyright, character, species, general, meta, lore), click a tag to search it, links to the post page and source.
- **Safe mode** (shield button) adds the site's safe-rating filter to every search.
- **Bookmarks** keep posts for later across all boorus; they persist in SQLite along with download history.
- Original vs. sample download quality, per post or as a default.
- Blacklist rules with Boorusama semantics: one rule per line, `tag1 tag2` requires both, `-tag` requires absence.
- Optional sidecar file next to each save: tags as `.txt` or all metadata as `.json`. Optional folder per site.
- Keyboard in details: `Esc` closes, `←` `→` step through posts, `S` saves, `B` bookmarks.

**Formats**: PNG, JPEG, WebP, GIF or the original bytes. WebP in and out goes through Skia, which Compose already ships.

**File names** use Boorusama's token grammar. Web downloads default to `{service}_{date}_{hash8}`, booru downloads to `{booru}_{id}_{md5:maxlength=8}`. Tokens: `service date hash8 hash md5 original extension uuid` plus `id tags artist character copyright general meta species rating score width height source search` for boorus. Options: `maxlength=N`, `limit=N`, `delimiter=comma|space|underscore|…`, `nomod`, `case=lower|upper|title`, `format=…` (date), `pad_left=N`, `single_letter`. Example: `{character:nomod,limit=2,delimiter=comma} by {artist} - {md5:maxlength=8}`. Collisions get ` (2)`, ` (3)`.

## Status

Phase 0 (spike), phase 1 and the Boorusama-inspired booru mode across six API families. Not yet done: history thumbnails, video preview for webm posts, edge-resize of the frameless window, macOS packaging, tabs, Zerochan/Sankaku/Hydrus clients.

## License

MIT. Dark Reader is bundled under its MIT license (see `browser/src/main/resources/scripts/darkreader.LICENSE.txt`).
