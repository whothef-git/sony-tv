# Al Quran Verse of the Day

The Prayer Time card replaces its active MyHadith section with **AL QURAN VERSE OF
THE DAY**. Original Arabic and Malay translation are fetched directly over HTTPS
from [Mengaji.my](https://www.mengaji.my/api-docs/). The provider identifies the
translation as Sheikh Abdullah Basmeih's Tafsir Pimpinan Ar-Rahman. This is a
third-party Quran API, not the official JAKIM prayer-time service.

The documented API needs no account, API key or external backend. `GET
https://api.mengaji.my/v1/random` returns the verse, Arabic, Malay, surah name and
reference together. If it repeats the previously shown ID, a single documented
`GET /v1/ayat/{id}` for the following global verse prevents an immediate repeat.
There is no retry loop, no bundled verse collection and no generated translation.
The full API wording, punctuation and diacritics are preserved.

## Rotation and offline behavior

- Rotation follows the current official JAKIM SGR01 prayer schedule: Subuh, Zohor,
  Asar, Maghrib and Isyak. Syuruk and midnight do not trigger a change.
- The interval is identified by the most recent prayer's actual timestamp. Before
  Subuh it remains the previous day's Isyak interval. Opening/reopening or restarting
  the app in the same interval keeps the same verse.
- Fetching happens only when Prayer Time is opened or remains visible when a new
  interval begins. Existing minute ticks and JAKIM refresh callbacks update the card;
  no new background service or polling loop is introduced.
- The latest fetched verse and interval are cached privately. It remains available
  offline within its interval. An earlier interval's content is not silently presented
  as the new verse. A new interval without Internet shows an unavailable message and
  retries no more than once per minute while visible.
- If official prayer times are unavailable, the card waits for them rather than
  presenting calculated fallback times as JAKIM rotation boundaries.

## Layout and transport

The current full-width card, dock visibility, prayer timeline and 6dp heading/content
gap are retained. Heading and Malay text use Alexandria Regular. Arabic uses the
official KFGQPC Hafs V3 Uthmanic font, with native bidirectional paragraph layout;
Malay is not retranslated. A blank line separates Arabic and Malay, and the surah/ayah
reference appears directly below the translation rather than in the bottom footer.
Existing whole-content auto-sizing fits the full verse and translation without
scrolling or ellipsis. Especially long verses consequently use smaller text. The
provider footer is hidden. Attribution remains documented here and in the repository.

The unmodified Unicode font comes from the [King Fahd Quran Printing Complex](https://fonts.qurancomplex.gov.sa/hafs-reading/),
version 3.0. SHA-256: `c9dd7e71a454565b13d89e5e6f2663a918acb0a20a23eea31eccb32a3aeec47a`.
Its embedded license grants free use, copying and distribution subject to its terms.
The complete embedded license accompanies it in the APK at `assets/licenses/kfgqpc_hafs.txt`.
Only a font is bundled; Quran verses and translations continue to come from the API.

The client validates required fields and reference ranges, allows only the documented
provider URLs, refuses redirects, limits responses to 128KiB and uses connection/read
timeouts. Default TLS chain and hostname validation remain enabled. The API is an
online dependency; no availability guarantee or JAKIM content endorsement is implied.

## Checks

`PrayerVerseChecks` covers the five boundaries, midnight continuity, Syuruk exclusion,
original-text parsing, missing data, repeat prevention, stable interval cache, network
failure/backoff and short/long bilingual text fitting. It also fetches a real verse over
HTTPS directly from the Sony TV. Mock fixtures are confined to tests, never shown as
live content. No smart-home controls are exercised.

Build with `:app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.PrayerVerseChecks`
and invoke `tv.widgetv.home.test/tv.widgetv.home.PrayerVerseChecks` using instrumentation.
The previous MyHadith provider/tests remain as historical source; the launcher no longer
instantiates that provider. Release shrinking removes unused code/resources.

Verified on 30 October 2026: optimized and debug builds passed; all 31 targeted checks
passed on the Sony Android 10 TV, including a real provider HTTPS request. The actual
Prayer Time screen displayed both scripts and the reference without scrolling. A cold
app reopen retained identical verse text and reference, with no crash entries for the
running release process. Prayer-boundary timing was simulated in automated checks;
testing did not wait for all five real prayer times. Other tabs and device controls
were outside this targeted validation. The preview APK is outside Git as
`../Octahedron-Quran-Verse.apk`; stable main is preserved in `../backups/pre-quran-verse/`.

31 October typography update: 34 checks passed on the TV, including actual API content
for 1:1 and 2:282 fitting completely with the new font, spacing and inline reference.
The optimized APK was installed and its Prayer Time screen visually inspected. No
crash entries were present for the running release process. The earlier Quran preview
is preserved in `../backups/pre-quran-typography/`.
