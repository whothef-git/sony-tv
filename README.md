# Octahedron v3.1

A calm, widget-first Android TV launcher with a living sky, local and world clocks, official JAKIM prayer times, Arabic Quran verses with Malay translation, expanded weather forecasts and integrated smart-home controls. Built on WidgeTV and customized for Sony BRAVIA.

Use the TV remote to move between **Home**, **Prayer Time**, **Weather** and **Smart Home**. Screen-tab navigation wraps from the last tab to the first and back.

**Current version:** v3.1 (`versionCode 14`). Adds medium-length ayah preference, a private current-artwork cache, a gentle verse fade, a 14sp preferred Malay translation size and stronger reference validation. Stable v3.0 remains the rollback build.

*All four previews below are short animated GIFs recorded directly on Sony BRAVIA on 1 November 2026 from the installed, optimized build matching that source. The 1920 × 1080 recordings are encoded at 960 × 540 and 15fps for the README. Clocks, Quran content, forecasts and device states reflect capture time; no device control commands were sent. These captures predate the NASA lunar-surface, lower dock and Home rain/wet-tile updates. Original-resolution stills and recording details are available in [capture details](docs/screenshots/README.md).*

## What's new in v3.1

- **More readable Quran card:** bounded medium-length selection, 14sp preferred translation with fit fallback, and one 220ms fade when the displayed prayer interval changes.
- **Offline current artwork:** one replaceable image in Android private cache, reloaded after app restart; Clear cache removes it safely. No Quran collection is bundled.
- **Reference consistency:** global verse ID is checked against all 114 chapter lengths before pairing Arabic artwork and Malay translation. This validates references, not the correctness of the provider's text.

## Existing v3 features

- **Living sky:** a subtle clear-sky tint, translucent sun and moon, a soft sun halo and horizon fades over the original geometric wallpaper. Time-specific moon illumination and waxing/waning orientation come from USNO, refreshed every fifteen minutes while visible.
- **Textured sun and moon:** a spherical Moon rendered from NASA LROC color and LOLA terrain data, plus decorative sun artwork at 50% opacity, a restrained 15% sky tint, and a small moon-phase label. The Moon uses a 4K source map and cached 384px rendering at its existing 90px display size, with no exterior glow. It is hidden when valid current phase data is unavailable.
- **Smooth clocks:** small local seconds alternate upward and downward as they change, with fixed digit positions to prevent clock shaking. The Saudi clock retains hours/minutes and uses the same alternating motion on minute changes.
- **Quran verses by prayer time:** Arabic and original Malay translation from Mengaji.my, changing at each of the five official JAKIM prayer times. Text automatically fits the available space; no verse collection is bundled.
- **Updated Weather screen:** Sunrise, Sunset and Next Rain replace the current-condition metric boxes. Eight upcoming hours show larger condition icons, temperature, rain chance and precipitation in millimetres, above a five-day outlook. Weather refreshes every ten minutes while the launcher is open.
- **Moving rain and wet apps:** Home and Weather share smooth per-refresh falling rain and bottom-edge splashes. Home also splashes on app-tile tops and adds cached water beads, trails and sliding droplets. Highlights fade before tile edges to avoid a rectangular wet overlay. Weather retains its clouds and rain tint. Only the current condition activates rain; hourly probability does not.
- **Clearer tab focus:** focused tab text and its short underline fade to the Islamic calendar's gold (`#D8B46A`), with a smooth 220ms transition and a measured 25px text-to-underline gap on the target TV. No pill background is used.
- **Dynamic ambient app lighting:** the focused app's colors illuminate the dock with smooth focus transitions.
- **Faster GO DAIKIN controls:** immediate optimistic display updates, debounced target dispatch and protection against background polling overwriting in-flight settings.
- **Controls for every online AC:** power, temperature, fan and operating-mode controls are available across registered online GO DAIKIN units, with consistent Online/Offline presence alongside Tapo and Kasa devices.
- **Remote navigation refinements:** wrapping screen tabs and a compact, top-aligned Prayer Time layout that keeps the full verse and translation visible without scrolling.

## The four screens

### Home

![Animated Home screen with local and Saudi clocks, Malay Hijri date, living sky and ambient app dock](docs/screenshots/octahedron-home.gif)

Local and secondary-city clocks, Gregorian and Malay Hijri dates, weather summaries, system shortcuts and an app dock sit over a geometric sky background. The configured dock apps appear in a row, with remaining apps available from the grid. The dock sits 24px lower at the target TV resolution, with a 10dp bottom container margin. Inputs, Wi-Fi and Settings remain accessible from the remote.

The local clock includes smaller seconds with alternating up/down transitions. The Saudi clock keeps its hours/minutes format. Moving up from the dock highlights the focused screen tab with gold text and underline; moving back down fades the gold away while the active-screen underline remains.

The living sky preserves the existing wallpaper and layout. The sun follows configured local sunrise and sunset timing. The moon moves along a decorative night arc; its position is an ambient illustration, not an astronomical altitude or moonrise prediction. Its illuminated fraction and phase orientation use an official USNO response for the current UTC date and time, refreshed in fifteen-minute intervals.

Recent moon data is cached privately for up to thirty minutes. If a fresh value is unavailable, the moon is omitted until it loads; the sun and wallpaper continue working offline. Position updates run while the launcher is visible and stop when paused. See [living-sky behavior and checks](docs/living-sky.md).

**Earlier sky timelapse — accelerated demonstration**

![Earlier TV timelapse showing the textured Moon and Sun moving along the ambient sky arcs](docs/screenshots/octahedron-sky-timelapse.gif)

Recorded on the Sony TV on 31 October 2026 during the approved textured-sky preview. This GIF condenses the saved recording of about 105 seconds to about 18 seconds. Only celestial travel was accelerated in that preview; the clock was not advanced, and it does not demonstrate lunar phases changing over a single night. It predates the latest clock-seconds, moon-label and gold-tab updates; the four card GIFs show the later UI layout, but also predate the NASA lunar-surface renderer. The temporary timelapse hook was removed from the normal launcher.

### Prayer Time

![Animated Prayer Time screen with official JAKIM SGR01 times and an Arabic Quran verse with Malay translation](docs/screenshots/octahedron-prayer-time.gif)

A full-width, read-only **official JAKIM SGR01 prayer timeline** accompanies **AL QURAN VERSE OF THE DAY**. Prayer schedules are cached and refreshed; unavailable official data is not replaced with calculated times.

The **AL QURAN VERSE OF THE DAY** card displays original Arabic and Malay translation from [Mengaji.my](https://www.mengaji.my/api-docs/), whose translation is based on Tafsir Pimpinan Ar-Rahman by Sheikh Abdullah Basmeih. Arabic uses online Madani ayah artwork from the Islamic Network CDN, matched by surah and ayah, followed by the Malay translation in Alexandria. The surah/ayah reference sits directly below the translation; the provider footer is hidden. Native auto-sizing preserves all wording without ellipsis or scrolling; very long verses use smaller text. The dock and app grid remain hidden.

The verse changes at Subuh, Zohor, Asar, Maghrib and Isyak using official JAKIM SGR01 times. It remains stable through Syuruk, midnight, reopening and app restarts within the same interval. An API request supplies each new verse; only the latest result is cached privately. A new interval requires Internet and a previous interval's verse is never silently substituted. This third-party Quran API is separate from JAKIM's prayer schedule. See [Quran verse details and checks](docs/prayer-quran-verse.md).

### Weather

![Animated Weather screen with falling rain, bottom splashes, eight hourly forecasts and a five-day outlook](docs/screenshots/octahedron-weather.gif)

The expanded screen uses [Open-Meteo](https://open-meteo.com/) for:

- Current temperature, condition and today's high/low, with Sunrise, Sunset and Next Rain boxes.
- Up to eight upcoming hourly forecasts with temperature, larger condition icons, rain probability and forecast precipitation in millimetres. Feels-like, humidity and wind rows are removed.
- A five-day outlook with daily high/low temperatures and rain probability.
- An update timestamp and saved-forecast labeling when cached data is shown after a refresh failure.

Weather refreshes when the saved reading is at least ten minutes old, checked on resume and each minute while the launcher is visible. On failure, saved data is labelled; readings older than three hours are hidden. More frequent fetching does not make the provider's model output a direct observation at the house.

Next Rain scans the next 24 hours for forecast precipitation with a rainy condition code. It shows **Now** for currently reported rain, an approximate hourly time otherwise, **None 24h** for a complete dry window, or a dash when data is unavailable. It is an estimate, not a minute-by-minute rain-onset alert. Sunrise and Sunset come from the same daily weather feed.

Local rain detection checks [RainViewer](https://www.rainviewer.com/) automatically every 10 minutes while the launcher is active. A fresh positive echo near the configured city can supplement clear, cloudy, foggy or drizzle conditions. The Weather card shows “Rain detected nearby” when using that signal. Open-Meteo supplies the forecast and the fallback condition.

Condition-driven weather atmosphere remains separate from the living-sky overlay. Falling rain, drifting clouds and bottom-edge splashes depend on current conditions, not forecast precipitation probability, and stop when their screen is inactive. Home has its own transparent rain layer with wet app tiles and tile-top impacts; Prayer Time and Smart Home remain clear. The app dock stays hidden on Weather to leave room for the five-day strip. Weather summaries for the local and secondary cities remain in the header. No Open-Meteo API key is required. See [rain behavior and TV checks](docs/weather-rain.md).

### Smart Home

![Animated Smart Home screen with five Daikin air conditioners, two Tapo plugs and one Kasa power-strip card](docs/screenshots/octahedron-smart-home.gif)

The integrated grid combines **GO DAIKIN air conditioners**, **Tapo P110 plugs** and a **Kasa KP303 three-outlet power strip**. Device controls open in floating dialogs, with remote focus returning to the grid when dismissed.

Open **Manage Devices** using MENU or by holding OK on the Smart Home tab. Scan or manually add a private LAN IP, enter account details privately, rename devices, assign rooms, reorder, refresh or remove entries.

**GO DAIKIN:** enter the account once through **Manage Devices → GO DAIKIN account**. The Java cloud client runs directly on the TV without an external backend. All registered online ACs expose power, temperature, fan and mode controls; offline controls are disabled. The UI updates immediately while cloud commands are dispatched, so a changed value on screen is not proof that the physical AC has applied it. Actual support depends on the unit and unofficial cloud API.

**Tapo and Kasa:** direct LAN transports support P110 KLAP v1/v2 and legacy AES login variants, plus KP303 legacy IOT outlets using device-reported identifiers. KP303 outlets share one card with individual outlet controls. Firmware compatibility and physical behavior require real-device verification.

Account details are stored using Android Keystore-backed encrypted storage. An explicit developer demo mode remains available; unsupported devices do not silently fall back to simulated controls. See [LAN protocols and limitations](docs/SMART-HOME-PROTOCOLS.md) and [earlier GO DAIKIN validation](docs/GO-DAIKIN-PHASE2.3.md). Those reports document earlier milestones and their restrictions; the current launcher enables controls for all online ACs.

## Build

Requires **JDK 17**, **Android SDK platform 34**, **build-tools 34.0.0** and the included **Gradle 8.7 wrapper**. Set `JAVA_HOME` and `ANDROID_HOME` to your installations.

From the repository root on Linux/macOS:

```bash
cd launcher
bash ./gradlew --no-daemon --max-workers=4 assembleDebug assembleRelease assembleDebugAndroidTest
```

On Windows, run `gradlew.bat assembleDebug assembleRelease assembleDebugAndroidTest` from `launcher/`.

| Output | Location under `launcher/` |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Optimized release APK | `app/build/outputs/apk/release/app-release.apk` |
| Instrumentation-test APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Release R8 mapping | `app/build/outputs/mapping/release/mapping.txt` |

Release builds enable R8 and resource shrinking. The current release variant uses the local debug signing identity; configure private release signing before external distribution. A newly generated key will not necessarily match an APK already installed on your TV. Keep the R8 mapping with each release.

**App identity:** the display name is **Octahedron**, with `versionName '3.0'` and `versionCode 13`. The package ID remains `tv.widgetv.home`; standard APK filenames are unchanged.

## Validation

The latest Home rain and wet-dock update completed debug, optimized release and instrumentation-test APK builds, with **54 WeatherRainChecks passing on the Sony TV** using weather fixtures. They cover forecast parsing, rain classification and movement, bottom splashes, dock collisions, wet-texture bounds and transparent edges, sliding beads and dry-state removal. Nuvio and beIN Sports were visually checked using a labeled rain preview, then live weather was restored. The final gold tab-focus build also compiled successfully and was installed and visually checked on the TV; its exact gold and 25px gap were measured from screenshots. Documentation captures on 1 November did not rerun those suites or exercise smart-device controls.

Earlier validation includes **14 ClockHeaderChecks**, **43 LivingSkyChecks** and **34 PrayerVerseChecks** on the Sony TV. The four existing host suites previously passed **304 synthetic checks**: 70 TP-Link protocol/state, 75 GO DAIKIN cloud, 75 temperature-control and 84 fan/mode/control checks. These host checks do not communicate with real devices or establish that every smart-device control works on hardware.

Host-test instructions are in [protocol-tests](launcher/protocol-tests/README.md) and the [GO DAIKIN proof of concept](launcher/godaikin-poc/README.md). They use Java 17 and the test-only `org.json:json:20240303` parser, which is not an APK dependency.

Android checks require an authorized test TV or compatible Android device. The default runner is `tv.widgetv.home.SmartHomeIntegrationChecks`. Feature-specific runners can be selected at build time:

```bash
# Run from launcher/. Build each test APK separately for its selected runner.
bash ./gradlew :app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.PrayerVerseChecks
bash ./gradlew :app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.LivingSkyChecks
bash ./gradlew :app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.WeatherRainChecks
bash ./gradlew :app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.ClockHeaderChecks
```

After installing the debug app and the matching test APK on an authorized device, invoke the selected runner, for example:

```text
adb shell am instrument -w tv.widgetv.home.test/tv.widgetv.home.PrayerVerseChecks
```

Quran-verse checks include a real provider API request; living-sky checks include official USNO calendar requests. They are separate from cloud onboarding checks. Earlier hardware results are recorded in the linked reports; power and unexercised AC settings must not be treated as physically verified from build or mock-test results.

## Configure and install on a TV

Personal defaults live in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml): local and secondary-city coordinates, time zone, weather units, dock apps, hidden packages, clock font and text size. The current prayer screen uses official JAKIM SGR01 data; older upstream calculation-method instructions do not describe this screen.

For the guided TV setup, see [`PROMPT.md`](PROMPT.md), [Sony BRAVIA notes](examples/sony-bravia-vu31.md) and [troubleshooting](docs/TROUBLESHOOTING.md). Enable developer/network debugging and authorize ADB on your own TV before installing. With a configured JDK/SDK and reachable TV:

```bash
TV_IP=<your-tv-ip> bash tools/build-install.sh
```

The script builds a debug APK, installs it and attempts to make the launcher HOME. It also synchronizes `content/thoughts.json` into the app resources. Use the direct Gradle commands above when you only need to compile the existing checkout.

The TV management tools back up settings and disable selected packages rather than uninstalling them. Review the device-specific instructions before changing TV settings, and close ADB when finished.

## Restore and recover

```bash
bash tools/restore.sh                        # re-enable apps and restore stock settings
bash tools/restore.sh com.example.app        # re-enable one app
bash tools/restore.sh --uninstall-launcher   # restore and remove this launcher
```

Set `TV_IP` through the environment or the ignored `tools/tv.conf`. Backups, disabled-package lists and screenshots live in the ignored `state/` directory. You can also re-enable disabled apps from the TV's Settings → Apps menu.

After firmware updates, see [update recovery](docs/UPDATES.md) and `tools/reapply.sh`. Keep automatic firmware updates enabled.

## Network and privacy

There is no external backend requirement or analytics integration. Network use depends on enabled features:

| Feature | Connection |
|---|---|
| Weather | Open-Meteo, with configured city coordinates |
| Official prayer schedule | JAKIM schedule provider |
| Quran metadata / Malay translation | `api.mengaji.my` |
| Madani Arabic ayah artwork | `cdn.islamic.network` |
| Time-specific moon phase | `aa.usno.navy.mil` |
| GO DAIKIN | Account authentication and device cloud API |
| Tapo/Kasa | Discovery and control on the local network |

Quran-verse, moon-calendar and prayer data are cached privately with feature-specific freshness rules. Normal TLS certificate and hostname verification remain enabled. The earlier MyHadith implementation remains historical source; see [its documentation](docs/daily-hadith.md).

Enter smart-home credentials privately on the TV. Do not commit credentials, TV backups or private signing keys. The original location permission supports showing the Wi-Fi network name; configured city coordinates supply weather and calendar requests.

## Repository guide

| Path | Purpose |
|---|---|
| `launcher/app/` | Main Android TV launcher and instrumentation checks |
| `launcher/godaikin-poc/` | Shared GO DAIKIN protocol sources and separate developer proof of concept |
| `launcher/protocol-tests/` | Host-only TP-Link protocol checks |
| `tools/` | Build/install, backup, restore, screenshots and TV management |
| `content/` | Bundled thought content and source verification; separate from live daily hadith |
| `docs/` | Design notes, feature behavior, historical validation and troubleshooting |
| `examples/` | Device-specific setup notes |
| `state/` | Ignored local TV backups and state |

The app targets Android SDK 34 with a minimum Android version of 9 (API 28). Earlier versions were tested on Sony BRAVIA Android 10 and Android 14 devices; this does not establish that every v3 feature has been tested on every model.

## Credits and license

Octahedron builds on **WidgeTV**, with inspiration from [cleanyourtv](https://github.com/Gathin23/cleanyourtv) and [Afnon](https://github.com/egyjs/afnon-android-tv-launcher). Included work also uses [adhan-java](https://github.com/batoulapps/adhan-java), Open-Meteo weather, official JAKIM/MyHadith content, USNO calendar data and the Cormorant Garamond, Outfit and Alexandria fonts.

See [`LICENSE`](LICENSE) for the MIT license and [`NOTICE.md`](NOTICE.md) for third-party notices. Third-party code, fonts and content retain their respective terms.
