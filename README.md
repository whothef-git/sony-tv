# WidgeTV

## Local TV build 2.0 — direct Smart Home integration

Smart Home now defaults to real local-device mode, with a persistent registry and encrypted TP-Link
account setup on the TV. Open **Manage Devices** using MENU, or hold OK on the Smart Home tab.
Scan or add a private LAN IP manually, enter TP-Link credentials privately, rename/assign rooms,
reorder, refresh, or remove devices. The existing UI and the other three screens are retained.

Direct Java transports cover P110 KLAP v1/v2 and legacy AES login variants, plus KP303 legacy IOT
outlets using device-reported identifiers. Commands are pending until device response and state
confirmation. This is a community-protocol port; compatibility and controls require real hardware
verification. Unsupported firmware shows an error, with no automatic demo fallback.

GO DAIKIN controls remain disabled until controller/API compatibility is verified. Five setup entries
remain in Manage Devices. Account login alone does not identify the controller's supported API.

See [protocols, security and limitations](docs/SMART-HOME-PROTOCOLS.md). No external backend or
smart-device cloud login is used. Developer demo mode remains an explicit, separate choice in Manage
Devices. Version 1.9 is preserved locally for rollback.

### Preserved Phase 1 demo interface

The fourth tab, Smart Home, is an isolated **DEMO MODE** interface: five Daikin air conditioners,
two Tapo P110 plugs and one Kasa KP303 power strip. No smart-device discovery, authentication or
network commands are implemented. Names and simulated readings come from an in-memory repository
and reset when the launcher process restarts.

Use the D-pad to move through the four-column device grid; UP from the first row returns to the
tabs. OK opens a floating control dialog, and BACK closes it and restores device focus. Daikin
power, temperature, mode and fan speed, Tapo power, and all three Kasa outlets work locally.
Online devices remain visible even when power is OFF. Lost connections show amber Connecting
for 45 seconds, then hide; reconnection restores preferred ordering. Focus recovers to the nearest
remaining device, and an empty-state message appears when all are hidden.

For developer simulation only, press the remote MENU key while Smart Home is selected
(`adb shell input keyevent 82` also works). Choose a device to simulate connection/power/settings,
or use All devices offline, All devices online, or Reset demo. These controls do not send network
requests. This hidden developer menu is intentionally available in the local Phase 1 demo build.

Smart Home hides the Saudi block and app launcher only on that screen. The main clock, local
weather, Gregorian/Malay Hijri dates, system icons, background and existing tab positions remain.

### Existing launcher features and optimized build

This customized build has Home, Prayer Time and Weather tabs, switched with the remote's D-pad.
Home keeps the clocks, compact Gregorian/Malay Hijri dates, weather summaries and app dock.
Prayer Time is a read-only, full-width official JAKIM SGR01 timeline with the daily thought.
Weather uses the existing Open-Meteo feed for six hourly and five daily forecasts, translucent
panels, and condition-driven background effects. Rain effects depend on current conditions,
not forecast precipitation probability. The weather and secondary UI share the Outfit font.
The existing ambient app focus effect is retained; navigation tabs use a neutral sliding underline.
Only the active tab is underlined; there is no full-width divider below the navigation.
The app glow fades in place when moving between the dock and screen tabs, avoiding a rectangular flash.
The local weather sits close to the main clock. Saudi time is right-aligned with current weather
beside its high/low range below. The Saudi flag is approximately 21.3 × 16 scaled units.
Gregorian and Malay Hijri dates use Outfit Regular at 15sp; local high/low and both Saudi
temperature readings use Outfit Regular at 13sp. The Saudi weather icon is independently sized at 14sp.

Version 1.6 was built and installed on Sony BRAVIA 4K UR3 (Android 10); all 16 on-device
Home/Prayer Time/Weather navigation checks passed. Earlier parser verification covered 30 checks.

AccuWeather integration is pending authenticated API access; this build does not claim to use
AccuWeather or embed an AccuWeather key. Prayer calculation/offset controls have been removed.
Official JAKIM schedules are cached and refreshed; unavailable official data is not presented as
calculated prayer times.

Build the Android app from `launcher/` with JDK 17 and Android SDK 34 using the Gradle wrapper.
On Windows, use `gradlew.bat assembleDebug`.
For the optimized local TV build, use `gradlew.bat assembleRelease`. Release builds enable R8
and resource shrinking, and disable debug preview hooks. The current local release variant uses
the existing debug signing identity for compatible in-place TV updates; configure private release
signing before external distribution. Keep the R8 mapping file with each build for crash diagnostics.

The optimized version 1.8 APK is 1,036,377 bytes, compared with version 1.7 at 4,841,421 bytes
(78.6% smaller), with all five bundled font files preserved. All 16 on-TV navigation checks passed;
Home, Prayer Time and Weather were visually checked, and YouTube launched successfully from the dock.

The upstream documentation below describes the original project and its original setup flow.

**A calm, widget-first home screen for Android TV: a sky that moves with the day, glanceable clocks,
weather and a daily thought, with prayer times built in.**

![The home screen at 12:30: a midday sky, big clock with weather, a second clock with a flag, the prayer timeline, a thought of the day and a dock of four apps](docs/screenshots/day-1230.png)

Most TV home screens are billboards. This one is a quiet window: the background shifts from dawn to
night through the day, the time and weather for you and someone far away sit in the corner, a single
thought sits in the middle, and your four apps wait at the bottom. Prayer times are built in, with a
timeline that shows where you are between one prayer and the next. You don't build it by hand: paste
[`PROMPT.md`](PROMPT.md) into Claude Code, answer a few questions, and it debloats the TV, configures the
launcher for your city and installs it. Everything is reversible.

## How it works

1. **On the TV**, turn on Developer options and USB/Network debugging (the prompt explains how).
2. **On your computer**, clone this repo, open [Claude Code](https://claude.com/product/claude-code) in it
   and paste [`PROMPT.md`](PROMPT.md).
3. **Answer its questions**: prayer times or not, your city and calculation method, an optional second
   clock city, weather units, which apps you use and which four go in the dock.
4. Claude Code **backs up** the TV, **debloats** it in small batches (disable only, never uninstall),
   **builds and installs** the launcher, makes it the home screen, **verifies** every step with
   screenshots pulled from the TV, and finally **closes ADB** again.

No root. No accounts. Nothing uninstalled. `tools/restore.sh` puts it all back.

## What you get

- **A sky that moves with the day.** The wallpaper drifts through dawn, morning, midday, golden hour,
  sunset and a starry night, timed to the real sun at your location, under a faint gold geometric
  pattern.
- **A big, quiet clock** in Cormorant Garamond or Outfit, with the date.
- **A second clock with a flag** for family abroad or the office, with the weekday when it's already
  tomorrow (or still yesterday) there.
- **Weather for both cities**: current temperature and icon beside each clock, with today's high and low.
  Open-Meteo, no key, fetched only while the home screen is on.
- **A thought of the day**: short hadith, Qur'an verses and plain productive reminders, every religious
  quote with a checked reference. Edit `content/thoughts.json` to make it yours.
- **An Apple TV style dock** with your four apps, a grid of everything else below, and a single gold
  focus ring that springs between them. Inputs, Wi-Fi and Settings sit in three round buttons.
- **Text size and clock font** you can change from the sofa.
- **A cleaner TV**: vendor promotions, telemetry and preloaded apps disabled, optional ad-blocking DNS.

### Prayer times, out of the box

- **A prayer timeline**: Fajr, Dhuhr, Asr, Maghrib and Isha on one line, with a dot that moves
  proportionally between the last prayer and the next.
- **Next-prayer countdown**: "Asr · in 3h 44m", in gold.
- **Hijri date** (Umm al-Qura) on the date line.
- **On-TV Prayer settings**: calculation method (10 methods), Asr school (Hanafi or Shafi'i), and
  per-prayer offsets of up to ±30 minutes to match your masjid. Calculated offline with adhan-java.
- **Friday**: a Jumu'ah Mubarak thought from a separate list of verified Qur'an verses and hadith.
- **Not your thing?** Set `prayer_enabled` to `false`: the timeline and Hijri date go away and the sky
  still follows sunrise and sunset.

## A day on the TV

The same screen at different times, simulated with the debug preview hooks (Hyderabad prayer times, a
second clock in Kitchener, Canada).

| | |
|---|---|
| ![3:00 am: a night sky with stars; the dot is left of Fajr, "Fajr · in 1h 59m"](docs/screenshots/night-0300.png) | ![5:30 am: a dawn sky in indigo and rose; the dot has just passed Fajr](docs/screenshots/dawn-0530.png) |
| **3:00** Night. Stars, and the dot creeping toward Fajr. The second clock shows yesterday. | **5:30** Dawn, just after Fajr. Dhuhr is next. |
| ![9:00 am: a clear morning blue; the dot halfway to Dhuhr](docs/screenshots/morning-0900.png) | ![12:30 pm: a bright midday sky; Asr in 3h 44m](docs/screenshots/day-1230.png) |
| **9:00** Morning blue, halfway to Dhuhr. | **12:30** Midday. Asr is next. |
| ![4:45 pm: a golden sky; Maghrib in 1h 7m](docs/screenshots/asr-1645.png) | ![6:20 pm: a sunset sky in plum and orange; Isha in 44m](docs/screenshots/maghrib-1820.png) |
| **16:45** Golden hour after Asr. | **18:20** Sunset after Maghrib. |
| ![10:00 pm: night again; the dot runs on past Isha toward tomorrow's Fajr](docs/screenshots/night-2200.png) | ![Friday 1:00 pm: the Jumu'ah Mubarak thought with a hadith reference](docs/screenshots/friday-1300.png) |
| **22:00** Night. The line runs on toward tomorrow's Fajr. | **Friday 13:00** Jumu'ah Mubarak, with a verified hadith. |

**Text size**, Normal / Large / Larger, changed on the TV:

| | | |
|---|---|---|
| ![Normal text size](docs/screenshots/text-normal.png) | ![Large text size](docs/screenshots/text-large.png) | ![Larger text size](docs/screenshots/text-larger.png) |

**Prayer settings** on the TV: Asr school, method, per-prayer offsets with the adjusted time, clock font
(and text size, below the fold):

![The Prayer settings panel over the home screen](docs/screenshots/prayer-settings.png)

## Customise

Everything personal lives in one file,
[`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml). The prompt
fills it in for you; to change something later, edit it and run `tools/build-install.sh`.

| Key | What it does | Example |
|---|---|---|
| `prayer_enabled` | Prayer timeline, Hijri date, prayer-timed sky | `true` |
| `prayer_city_label`, `prayer_lat`, `prayer_lng` | Your city: prayer times, sky, weather | `Hyderabad`, `17.3850`, `78.4867` |
| `prayer_method` | Starting calculation method | `KARACHI` |
| `prayer_madhab` | Starting Asr school | `HANAFI` or `SHAFI` |
| `second_clock_enabled` | The second clock and its weather | `true` |
| `second_zone`, `second_flag` | Its IANA time zone and flag drawable | `America/Toronto`, `flag_ca` |
| `second_lat`, `second_lng` | Its coordinates, for weather | `43.4516`, `-80.4925` |
| `weather_enabled`, `weather_units` | Weather on/off, `celsius` or `fahrenheit` | `true`, `celsius` |
| `dock_packages` | The dock apps, left to right | Netflix, YouTube, ... |
| `hidden_packages` | Apps never shown in the grid | the dock apps, vendor leftovers |
| `pattern_mode` | `zellige` every day or `rotate` daily | `zellige` |
| `thoughts_enabled` | Thought of the day | `true` |
| `clock_font`, `text_size` | Starting values for the panel | `cormorant`, `normal` |

Flags shipped: Canada, India, UK, USA, UAE, Saudi Arabia, Pakistan. Claude Code draws a new one if you
need it. The thought list is in [`content/`](content/README.md). How the screen is built:
[`docs/DESIGN.md`](docs/DESIGN.md).

## Restoring

```bash
tools/restore.sh                        # re-enable everything, stock home back, DNS and screensaver restored
tools/restore.sh com.example.app        # re-enable one app
tools/restore.sh --uninstall-launcher   # everything, and remove the launcher too
```

Or on the TV, without a computer: Settings > Apps > See all apps > Show system apps, pick the app,
**Enable**. Everything that was disabled is listed in `state/disabled.txt`.

## After a firmware update

Small updates leave everything in place. A big one (say Android 14 to 15) can bring the stock home
screen and some vendor apps back. Nothing breaks; you just see the old home screen. Turn debugging on,
run `tools/reapply.sh` (about two minutes; it re-disables what came back and makes this launcher home
again), and close ADB. Details in [`docs/UPDATES.md`](docs/UPDATES.md). Keep automatic updates on.

## Privacy

- No accounts, no analytics, no ads, no tracking.
- One network call: weather from [Open-Meteo](https://open-meteo.com), with your two cities'
  coordinates, only while the home screen is in front, at most every 30 minutes. Turn it off with
  `weather_enabled`.
- Prayer times are calculated on the TV.
- The location permission is granted only so the Wi-Fi button can show your network's name.
- ADB is switched off and verified closed at the end of setup.

## Tested on

Tested on a Sony BRAVIA (VU31 platform), Android 14 Google TV, 2 GB RAM. Notes and the full disable
list: [`examples/sony-bravia-vu31.md`](examples/sony-bravia-vu31.md).

It should work on Android TV / Google TV 9 and newer (the launcher's `minSdk` is 28); older versions
are untested. Reports and `examples/<model>.md` pull requests are welcome.

The screenshots come from the original build on that TV; this generalised build compiles but has not
yet been run on hardware. If you try it, please open an issue with your TV model.

## Repo map

| Path | What |
|---|---|
| `PROMPT.md` | The prompt to paste into Claude Code |
| `launcher/` | The launcher (Java, single activity). Settings in `res/values/config.xml` |
| `tools/` | `backup`, `disable`, `restore`, `reapply`, `build-install`, `shot` scripts and `adb-closed.py` |
| `content/` | Thought of the day and its verification log |
| `docs/` | Updates, troubleshooting, design notes, screenshots |
| `examples/` | Per-TV notes |
| `state/` | Created on your machine: backups, disabled list, screenshots, your choices (gitignored) |

## Inspiration and credits

- [**cleanyourtv**](https://github.com/Gathin23/cleanyourtv) by Gathin: the idea of a prompt that has a
  coding assistant debloat a TV and build it a clean launcher. This project started from it.
- [**Afnon**](https://github.com/egyjs/afnon-android-tv-launcher) by egyjs: the prayer logic (adhan-java
  setup, offsets, Hijri date) and the lapis-and-gold design language with its star lattice.
- [**adhan-java**](https://github.com/batoulapps/adhan-java) by Batoul Apps: prayer-time astronomy.
- Fonts: [Cormorant Garamond](https://github.com/CatharsisFonts/Cormorant),
  [Outfit](https://github.com/Outfitio/Outfit-Fonts), [Alexandria](https://github.com/Gue3bara/Alexandria)
  (SIL OFL 1.1).
- Weather: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- Quotes checked against [quran.com](https://quran.com) (Sahih International) and
  [sunnah.com](https://sunnah.com).

Full licence texts: [`NOTICE.md`](NOTICE.md).

## License

MIT, see [`LICENSE`](LICENSE). Third-party parts keep their own licences ([`NOTICE.md`](NOTICE.md)).
