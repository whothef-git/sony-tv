# Approved living sky

The original geometric wallpaper, header, dock and launcher layouts remain in place. A clear-sky tint blends over the existing wallpaper at 15% opacity. The sun and moon are independently drawn at 50% opacity, with soft halos and a brief horizon fade. No new cloud or rain effects are added. The existing Weather-tab atmosphere is unchanged.

The sun follows the existing local sunrise and Maghrib/sunset times. The moon follows a decorative night arc between sunset and sunrise; this is an ambient illustration, not a precise astronomical altitude/azimuth or moonrise prediction. The moon's illuminated fraction and waxing/waning side use the local date's official USNO calendar response, not a fixed full-moon texture or a Hijri-day approximation.

`MoonCalendar` requests `https://aa.usno.navy.mil/api/rstt/oneday` with the configured local coordinates, date and UTC offset. The [official API documentation](https://aa.usno.navy.mil/data/api) defines `curphase` and `fracillum`. One successful response is cached privately per date; failed requests have a 30-minute retry gap. HTTPS certificate and hostname validation remain enabled. If the current date's phase is unavailable, the moon is omitted until it loads; no previous day's phase is presented as current. The sun and original wallpaper continue working offline.

Positions update every ten seconds while the launcher is visible, using normal time. Minute ticks handle date and sunrise/sunset changes. Animation callbacks stop when paused, and the calendar executor shuts down with the activity. No continuous 30fps loop, new service, framework, or production timelapse is included.

`LivingSkyChecks` covers lunar parsing and invalid responses, phase orientation, midnight arc math, translucent rendering, offline date caching, and a real official HTTPS calendar request on the TV. Build its test APK with `:app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.LivingSkyChecks` and run `tv.widgetv.home.test/tv.widgetv.home.LivingSkyChecks`.
