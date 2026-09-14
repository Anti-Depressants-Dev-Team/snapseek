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

**Websites (Pinterest, Pixiv, DeviantArt, …)**

- Pick a site on the home screen. Pinterest lets you choose a regional mirror.
- **Right-click an image** for Save as PNG / JPEG / GIF / Save original.
- **Alt+click an image** to save it in your default format without a menu.
- Pinterest thumbnails and Pixiv "master" renders are upgraded to the original file before saving. Downloads reuse the browser's login cookies.

**Boorus (Safebooru built in; add gelbooru.com, rule34.xxx, tbib.org and other Gelbooru 0.2 sites as custom services)**

Borrowed from [Boorusama](https://github.com/khoadng/Boorusama): boorus are browsed natively through their API instead of a web page.

- Tag search with autocomplete, `-tag` exclusion, `rating:` and `sort:` metatags, and a recent-searches row.
- Masonry grid with infinite scroll. Hover a post to save it in one click.
- Post details: full-size preview, tags grouped and coloured by category (artist, copyright, character, general, meta), click a tag to search it, links to the post page and source.
- Original vs. sample download quality, per post or as a default.
- Blacklist rules with Boorusama semantics: one rule per line, `tag1 tag2` requires both, `-tag` requires absence.
- Optional sidecar file next to each save: tags as `.txt` or all metadata as `.json`.
- Keyboard: `Esc` closes details, `←` `→` step through posts, `S` saves.

**File names** use Boorusama's token grammar. Web downloads default to `{service}_{date}_{hash8}`, booru downloads to `{booru}_{id}_{md5:maxlength=8}`. Tokens: `service date hash8 hash md5 original extension uuid` plus `id tags artist character copyright general meta rating score width height source` for boorus. Options: `maxlength=N`, `limit=N`, `delimiter=comma|space|underscore|…`, `nomod`, `case=lower|upper|title`, `format=…` (date), `pad_left=N`, `single_letter`. Example: `{character:nomod,limit=2,delimiter=comma} by {artist} - {md5:maxlength=8}`. Collisions get ` (2)`, ` (3)`.

## Status

Phase 0 (spike) plus most of phase 1 and the Boorusama-inspired booru mode. Not yet done: persistent history with thumbnails (SQLite), WebP output (Skia transcoder), video preview for webm posts, bulk download of a whole search, edge-resize of the frameless window, macOS packaging, tabs.

## License

MIT. Dark Reader is bundled under its MIT license (see `browser/src/main/resources/scripts/darkreader.LICENSE.txt`).
