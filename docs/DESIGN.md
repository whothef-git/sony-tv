# Design notes

How the home screen is put together, for anyone changing it. The source is small (about 2,300 lines
of Java in `launcher/app/src/main/java/`), with no frameworks beyond AndroidX RecyclerView and Palette.

## Principles

- **Glanceable from the sofa.** Big clock, few words, nothing that needs reading twice. Every text
  colour keeps at least 4.5:1 contrast against the sky behind it at every time of day.
- **Calm.** No animation loop, no video, no blur. The screen redraws on the minute tick, on a D-pad
  press, and when the page scrolls. The sky changes colour slowly over 20 minutes.
- **Private.** One network call (weather), only while the home screen is in front.
- **Light.** About 55 to 60 MB of memory on a 2 GB TV; most of it is app banners and the variable fonts.

## Colour

Lapis and gold, adapted from Afnon's tokens (`Lapis.java`, `res/values/colors.xml`):

| Token | Hex | Used for |
|---|---|---|
| lapis-900 | `#0C1B33` | scrim, focused-button icon, panel text on gold |
| lapis-800 | `#12264A` | dock band, panels, grid tiles |
| gold-400 | `#D8B46A` | next prayer, Hijri date, focus ring, thought tag, pattern lines |
| plaster-100 | `#EFE7D6` | primary text |
| mist-400 | `#9FB0CC` | secondary text, second clock, high/low |

## The day sky (`SkyView`)

A three-stop vertical gradient whose colours follow six periods of the day:

| Period | Starts at | Feel |
|---|---|---|
| dawn | Fajr | indigo to rose |
| morning | sunrise | clear blue |
| midday | Dhuhr | bright blue |
| golden | Asr | blue to warm gold |
| sunset | Maghrib | plum to orange |
| night | Isha | near black, 40 fixed stars |

With prayer times on, the boundaries are the actual prayer times for your city and method. With
`prayer_enabled=false` the same astronomy (adhan-java) still gives dawn, sunrise, solar noon,
mid-afternoon, sunset and nightfall for your coordinates, so the sky moves with the real sun either way.
At extreme latitudes where the sun never sets or never gets low enough, fixed hours are used instead.

After each boundary the colours crossfade over 20 minutes, one step per minute tick.

On top of the sky, in order:

1. **Scrim**: lapis-900 at 90% at the top fading to 0 at 68% of the height, so the header text stays
   readable on the brightest sky.
2. **Pattern**: a gold geometric tile at 13% opacity, full strength at the top edge and fading out by
   58% of the height. It sits above the scrim (under it, it disappears). `pattern_mode=zellige` shows an
   octagon-and-square zellige every day; `rotate` cycles 8-point star, hex girih, interlaced circles and
   zellige by day of the year. Tiles are drawn in code (`Patterns.java`) and cached as a bitmap shader.
3. **Dim**: as you scroll down to the app grid, a lapis layer fades in up to 78%.

## Layout (1080p, first screen)

- **Top left**: the clock (Cormorant Garamond Light with lining figures, or Outfit Thin; no AM/PM),
  the current temperature and icon on its baseline, and the date line:
  `Wednesday 21 October · 10 Jumada al-Ula 1448 · ↑32° ↓23°` (Hijri date only with prayer times on).
- **Top right**: three round buttons (Inputs, Wi-Fi, Settings). The Wi-Fi network name appears only
  while the Wi-Fi button is focused. Under them the second clock: flag, time (with the weekday when the
  date differs from yours), weather, and its high/low.
- **Middle**: the prayer timeline, then the thought of the day.
- **Bottom**: a slim dock band with your four apps. Below the fold, an "Apps" grid of everything else,
  four columns.

## Prayer timeline (`PrayerTimeline`)

Five equal columns: Fajr, Dhuhr, Asr, Maghrib, Isha. Column centres are `width / 5 × (i + 0.5)`.

- Between two prayers the dot sits at the time-proportional point:
  `x = x(prev) + (x(next) − x(prev)) × (now − t(prev)) / (t(next) − t(prev))`.
- The night is drawn as two dimmer half-column stubs: before Fajr the dot travels from yesterday's Isha
  (line start) to Fajr; after Isha it travels from Isha to tomorrow's Fajr (line end).
- The gold fill runs from Fajr to the dot.
- The next prayer's time is gold and slightly larger, labelled `Asr · in 3h 44m`.
- Offsets from the panel are applied through adhan-java's `adjustments` before the times are computed,
  so the timeline, countdown and sky all agree.

## Focus and navigation

The activity owns the D-pad; no view is focusable. Focus is a (zone, index) pair and one gold ring
(`GlassHighlight`) springs to whatever it points at.

- Zones, top to bottom: buttons, timeline (or the clock header without prayer times), dock, grid.
  Inputs dropdown and the settings panel are modal.
- Left/Right stop at the ends of each row. Up from the first grid row returns to the dock tile you came
  from. HOME while already home jumps to the first dock tile; returning from an app keeps your place.
- OK on the timeline opens **Prayer settings**: Asr school, method (10), five per-prayer offsets
  (−30 to +30 minutes, each showing the adjusted time), clock font, text size. Left/Right changes a
  value and it applies at once; OK or Back closes it. Without prayer times, OK on the clock opens
  **Display settings** with just clock font and text size.

## Text size

Normal, Large and Larger scale everything except the main clock and the dock:

| Item (sp) | Normal | Large | Larger |
|---|---|---|---|
| Temperature beside the clock | 24 | 28 | 32 |
| Date line | 14 | 16 | 18 |
| Second clock | 22 | 26 | 30 |
| Prayer times / names | 15 / 11 | 17 / 13 | 19 / 15 |
| Thought | 19 | 21 | 23 |

## Weather (`Weather`)

One Open-Meteo request for both cities: current temperature, WMO weather code, day/night, and today's
high/low in each city's own local day. Fetched from `onResume` and the minute tick (whose receiver is
only registered while the launcher is resumed), when the cache is at least 10 minutes old, at most one
attempt per 10 minutes. Data older than 3 hours is hidden rather than shown stale. Nine thin-line icons
cover seven weather types.

The Weather hero's three chips show today's Sunrise, Sunset and Next Rain. Solar times use the same
Open-Meteo daily response. Next Rain scans the next 24 hours for nonzero forecast precipitation with
a rainy condition code, independently of rain probability. It shows an approximate hourly time, Now
when the current condition is rainy, None 24h for a complete dry window, or a dash if data is missing.
The hourly strip shows time, condition icon, temperature, rain chance and forecast precipitation in
millimetres (one decimal place; missing values show a dash), with Feels, Humidity and Wind
removed. These are forecast estimates, not rain-onset observations.

## Thought of the day (`Thoughts`)

See `content/README.md`. Cormorant Garamond Italic in curly quotes, at most two lines, with the source in
small type when there is one. Fridays show the gold "Jumu'ah Mubarak" tag and an entry from the Friday list.

## Flags

3:2 vector drawables with 2-unit rounded corners in a 30×20 viewport (`res/drawable/flag_*.xml`). They
are drawn small (about 22×15 px), so detail is simplified: no tiny stars on the US flag, an abstract line
for the Saudi inscription. `flag_pk.xml` is the simplest template for drawing a new one.
