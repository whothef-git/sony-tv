# The prompt

*Builds on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin (the debloat-and-launcher
workflow) and [Afnon](https://github.com/egyjs/afnon-android-tv-launcher) by egyjs (prayer logic and the
lapis-and-gold look). Both MIT.*

**Before you paste it:**

- It works on **Android TV and Google TV** sets (Sony, TCL, Xiaomi, Hisense, Philips, Chromecast with
  Google TV and most others) running **Android 9 or newer**. Not on Samsung (Tizen), LG (webOS) or
  Fire TV.
- Clone this repo and open Claude Code **inside it**, on a computer on the same Wi-Fi as the TV.
- On the TV: Settings > System > About, press OK on **Build** seven times. Back in Settings > System >
  Developer options, turn on **USB debugging** and, if listed, **Network debugging** (also called
  "ADB debugging" or "Network/ADB debugging"). Note the TV's IP address under Settings > Network.
- Keep the remote handy: the TV will ask "Allow USB debugging?" once.
- You need about 3 GB free for the build tools if you don't have Android Studio. Claude will tell you
  what to download.

Copy everything between the lines.

---

You are setting up my Android TV with the launcher in this repo: debloat it safely, install the
launcher customised to my answers, and verify everything with screenshots from the TV. Work on the real
device, in small steps, and stop to ask me whenever something depends on my preferences. Everything you
change must stay reversible.

This repo already has what you need. Read `README.md`, `docs/TROUBLESHOOTING.md` and any file in
`examples/` that matches my TV before starting. Use the scripts in `tools/` rather than writing your own;
the only file in the launcher you should need to edit is `launcher/app/src/main/res/values/config.xml`.
All per-TV output goes in `state/` (gitignored).

## 1. Connect

- Ask me for the TV's IP address (or find it from the router/ARP table and confirm with me), then write
  it to `tools/tv.conf` as `TV_IP=<address>`. Every script reads it from there.
- Connect with `adb connect <ip>:5555`. Use `platform-tools/` in the repo if adb isn't installed: tell me
  where to download Android platform-tools and unzip it there. Tell me **before** the "Allow USB
  debugging?" prompt appears so I can accept it.
- If port 5555 refuses, the TV may only offer **Wireless debugging**: ask me to open Developer options >
  Wireless debugging > Pair device with pairing code, then `adb pair <ip>:<port>` with the code, `adb
  connect` to the port shown, and optionally `adb tcpip 5555`.
- On Windows Git Bash, set `MSYS_NO_PATHCONV=1` for any adb command you type by hand, or device paths
  like `/data/local/tmp` get rewritten. (`tools/env.sh` does this for the scripts.)
- Never use `adb install`: on Android 14 it can hang waiting for an invisible prompt. Push the APK to
  `/data/local/tmp` and run `pm install -r` (`tools/build-install.sh` does this).
- Read `getprop ro.product.model`, `ro.build.version.release` and the RAM, and tell me what you found.

## 2. Back up before changing anything

Run `tools/backup.sh`. It saves package lists, HOME apps, `getprop` and all settings to
`state/backup-<date>/`. Check that the folder is not empty. `tools/restore.sh` uses this backup to put
Private DNS, the screensaver and the screen-off timeout back.

## 3. Ask me

Ask these as checklist questions (use AskUserQuestion when you have it), a few at a time:

**About the TV**
1. TV model (confirm what getprop said) and what is plugged into its USB ports. Some TVs cut USB power
   while debugging is on; warn me if mine is one of them.
2. Which streaming apps do I use? Show me the installed ones as a checklist; I tick what to keep.
3. Keep or remove: voice assistant, Chromecast built-in, AirPlay, smart-home / Alexa integrations,
   Ambient mode screensaver, firmware update from USB. Default to keep for anything I'm unsure of.
4. Which four apps go in the dock, in which order (left to right)?

**The widgets**
5. **Do you want prayer times?** (timeline with next-prayer countdown, Hijri date, Friday reminders)
   - If **yes**: which **city**? Look up its coordinates (4 decimals) and suggest the calculation method
     usual for that country, then let me confirm or change it:
     India, Pakistan, Bangladesh, Afghanistan: `KARACHI` · Saudi Arabia: `UMM_AL_QURA` · Egypt, much of
     Africa: `EGYPTIAN` · USA, Canada: `NORTH_AMERICA` (ISNA) · UK, Europe: `MUSLIM_WORLD_LEAGUE` or
     `MOON_SIGHTING_COMMITTEE` (ask which my masjid follows) · UAE: `DUBAI` · Kuwait: `KUWAIT` ·
     Qatar: `QATAR` · Singapore, Malaysia, Indonesia: `SINGAPORE` · elsewhere: `MUSLIM_WORLD_LEAGUE`.
     Then ask the **Asr school**: Hanafi (later Asr) or Shafi'i (Shafi'i, Maliki, Hanbali).
     Tell me that per-prayer offsets to match my masjid can be set later on the TV.
   - If **no**: still ask my **city**: the sky follows the real sunrise and sunset there, and the
     weather uses it.
6. **A second clock?** For family abroad or work. If yes: which city? Use its IANA zone (for example
   `America/Toronto`, `Europe/London`, `Asia/Dubai`) and its coordinates for weather. Flags shipped:
   `flag_ca`, `flag_in`, `flag_gb`, `flag_us`, `flag_ae`, `flag_sa`, `flag_pk`. For any other country,
   draw `res/drawable/flag_<cc>.xml` yourself as a 3:2 VectorDrawable in a 30×20 viewport with the same
   rounded clip-path as `flag_pk.xml`, simplified for 22 px wide, and show me a screenshot of it.
7. **Weather** on or off, and °C or °F? (Open-Meteo: free, no account, the launcher's only network use.)
8. **Thought of the day** on or off? (See `content/README.md`; the shipped list is Islamic on Fridays.)
9. **Clock font**: Cormorant (elegant serif) or Outfit (thin sans)? **Text size**: normal, large or
   larger? Both can be changed later on the TV.

**The TV's settings**
10. **Ad-blocking Private DNS** (`dns.adguard-dns.com`)? Explain it blocks many in-app ads and trackers
    but **not YouTube ads**, and can occasionally break an app's login.
11. **Screen-off timeout** when idle (for example 10 or 30 minutes, or never)?

Write my answers to `state/choices.md` as you go, so a later session (or a factory reset) can repeat them.

## 4. Debloat, carefully

- List packages for user 0 (`pm list packages --user 0`, plus `-s`, `-3`, `-d`) and sort them into:
  vendor telemetry/analytics, vendor ads/promotions/content rows, background extras (demo modes, help,
  duplicate cast/share services), unused system apps (camera, gallery, print, calendar sync), preloaded
  streaming apps, and **keep**. Show me the plan as a table before disabling anything.
- **Never disable** what the TV needs: Live TV and HDMI/tuner inputs and their providers, the input
  picker, remote/Bluetooth/voice-remote services, the on-screen keyboard(s), Play Store and Play
  services, Settings, system UI, and any `FallbackHome` activity. Vendor names are cryptic: run
  `dumpsys package <name>` (activities, services, receivers) before guessing, and ask me if still unsure.
- **Never launch** a factory, service-menu or test package (names with `factory`, `servicemenu`, `fac`,
  `AT`, `hkcfac` and similar) to see what it does, not even read-only. On some boards opening one flips
  a factory flag that breaks inputs on the next boot. `dumpsys package` is enough.
- Disable with `tools/disable.sh <batch-name> pkg ...` (one `pm disable-user --user 0` per package,
  logged to `state/disabled.txt`). Never uninstall a system package.
- Small batches, one category at a time. After each batch take a screenshot with `tools/shot.sh` and look
  at it, and check that the TV's home, inputs and one app still work. A batch can take something
  load-bearing with it.
- Only touch streaming apps, the assistant, Chromecast and update services according to my answers.
- Apply my Private DNS choice (`settings put global private_dns_specifier dns.adguard-dns.com` then
  `settings put global private_dns_mode hostname`), open an app to check it still plays, and my
  screen-off timeout (`settings put system screen_off_timeout <ms>`).
- After the last batch, **reboot once** and compare `pm list packages -d --user 0` with
  `state/disabled.txt`. Packages the firmware turned back on by itself go on the
  `# firmware-reenables:` line at the top of `state/disabled.txt` (space separated) and, if they show up
  as apps, into `hidden_packages`. Don't fight them after every boot.

## 5. Configure and install the launcher

- Edit `launcher/app/src/main/res/values/config.xml` with my answers: `prayer_enabled`,
  `prayer_city_label`, `prayer_lat`, `prayer_lng`, `prayer_method`, `prayer_madhab`,
  `second_clock_enabled`, `second_city_label`, `second_zone`, `second_flag`, `second_lat`, `second_lng`,
  `weather_enabled`, `weather_units`, `dock_packages` (and the same packages in `hidden_packages`),
  `thoughts_enabled`, `clock_font`, `text_size`. Show me the diff.
- Run `tools/build-install.sh`. If it reports missing JDK 17 or Android SDK pieces, tell me exactly what
  to install (or download the portable versions into `toolchain/` with my OK) and run it again.
- **Home takeover** (follow `docs/TROUBLESHOOTING.md#home-takeover`):
  - `set-home-activity` alone can print Success and change nothing on Google TV.
  - Disable the stock launcher with `tools/disable.sh home <stock launcher package>`.
  - If the setup wizard (`com.google.android.tungsten.setupwraith`) then grabs HOME, first run
    `dumpsys wifi | grep -o 'cname=[^ ]*'`. If any saved network belongs to setupwraith, **stop**:
    disabling it would delete that network and drop the TV off Wi-Fi and ADB. Ask me to forget and
    re-join the network in Settings, check again, and only then disable it.
  - Never disable the `FallbackHome` activities.
  - Done means `dumpsys activity activities | grep mFocusedApp` names our package after pressing HOME.
    Not "set-home-activity said Success".
- On Android 9–10 the Wi-Fi button needs **Location** turned on (Settings > Location, or the TV's
  privacy settings) to show the network name; otherwise it just shows "Wi-Fi". Tell me if that applies.
- After taking over HOME, ask me to press the remote's dedicated buttons (Input, app buttons, Settings)
  and tell you which ones no longer work, and note them in `state/choices.md`.

## 6. Verify with screenshots

Take and **look at** each screenshot with `tools/shot.sh`; fix what you see before moving on:

- Home at rest: clock, date, weather beside the clocks, the second clock with the right flag, the
  timeline (if prayer is on) with sensible times for my city, the thought, the dock in my order.
- D-pad: Up to the timeline and OK opens Prayer settings (or OK on the clock opens Display settings
  without prayer times); change an offset and back; Down into the grid and Up again; open a dock app
  and press HOME to come back.
- A simulated day with the debug preview hooks (see `docs/TROUBLESHOOTING.md#preview-hooks-debug-builds`):
  `fake_time` 03:00, 05:30, 09:00, 12:30, 16:45, 18:20, 22:00, and `fake_date` set to the next Friday at
  13:00. Check the sky period, the dot position and the countdown in each, and that text stays readable
  on every sky. Reset all hooks to `auto` afterwards.
- Compare today's prayer times with a trusted local timetable or my masjid's; small differences are
  fixed with offsets in the panel, large ones mean the wrong method or coordinates.
- Live TV and HDMI screenshots come back black; check `topResumedActivity` instead.
- Check for crashes: `logcat -d | grep -iE "FATAL|AndroidRuntime"` filtered to our package.

## 7. Finish

- Complete `state/choices.md`: model, everything I chose, the disabled list by category, the
  firmware re-enables, which remote buttons misbehave, and the screenshots you took.
- Explain in two lines: `tools/restore.sh` undoes everything (or one app), and after a big firmware
  update `tools/reapply.sh` puts it all back (see `docs/UPDATES.md`). Keep automatic updates on.
- **Close ADB.** Port 5555 is unencrypted and must not stay open. Ask me to, on the TV:
  1. Developer options > **Revoke USB debugging authorisations**.
  2. Turn **USB debugging** and **Network debugging** off.
  3. Restart the TV.

  Then run `python3 tools/adb-closed.py <ip>` (on Windows `py -3 tools\adb-closed.py <ip>`). It must
  print `CLOSED (connection refused)`. A timeout or "no route to host" proves nothing; ask me to check
  the TV is on and try again.

---

Expect five to ten questions and about twenty to forty minutes, most of it the first build.
Everything is reversible with `tools/restore.sh`.
