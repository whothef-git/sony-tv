# Sony BRAVIA (VU31 platform), Android 14 Google TV

Tested October 2026. Use this as a starting point for a similar Sony set; on yours, still list the
packages, check each with `dumpsys package`, and ask before touching anything you might use.

## Device

| | |
|---|---|
| OS | Android 14, Google TV |
| SoC | Realtek (packages under `com.realtek.*`) |
| RAM | 2 GB |
| Launcher memory | about 55 to 60 MB PSS |
| ADB | Network debugging on port 5555 worked directly; no pairing needed |
| Sideloading | `pm install -r` from `/data/local/tmp` works; `adb install` can hang |

## Disabled (46 packages, all with `pm disable-user --user 0`, all reversible)

**Sony promotions and content**
`com.sony.dtv.promos`, `com.sony.dtv.sonyselect`, `com.sony.dtv.sonytab`,
`com.sony.dtv.multiscreendemo`, `com.sony.dtv.helpguidecontents`, `com.sony.dtv.smarthelp`,
`com.sony.qrmt`, `com.sonypicturescore`

**Sony and Google telemetry and setup leftovers**
`com.sony.dtv.ecodashboard` (re-enabled at boot, see quirks), `com.google.android.feedback`,
`com.google.android.partnersetup`

**Smart home, Alexa and casting extras** (the household did not use them)
`com.sony.dtv.m2.seeds.auth`, `com.sony.dtv.m2.seeds.iot`, `com.sony.dtv.m2.seeds.iot.remotestart`
(all re-enabled at boot), `com.sony.dtv.airplayapp` (AirPlay)

**Unused system apps**
`com.android.camera2`, `com.android.cameraextensions`, `com.android.gallery3d`,
`com.android.printspooler`, `com.android.wallpaper.livepicker`, `com.android.providers.calendar`,
`com.google.android.syncadapters.calendar`

**Google extras**
`com.google.android.apps.tv.dreamx` (Ambient screensaver; the screen just turns off after the timeout),
`com.google.android.play.games`, `com.google.android.youtube.tvmusic`

**Preloaded streaming and utility apps the household did not use** (India-market preloads)
`com.sonyliv`, `com.graymatrix.did` (ZEE5), `com.jio.media.jiotvplus`, `com.jio.media.stb.ondemand`,
`com.ryzmedia.tatasky`, `com.tataskymore.open.uat`, `com.suntv.sunnxt`, `ahaflix.tv`,
`com.balaji.alt`, `com.viewlift.hoichoi`, `com.yupptv.androidtv`,
`com.discoveryplus.tv.android`, `com.apple.atve.androidtv.appletv`, `com.crunchyroll.crunchyroid`,
`com.viki.android`, `com.dailymotion.dailymotion`, `com.mxtech.videoplayer.television`,
`com.tcl.browser`, `com.yablio.sendfilestotv`

**Home takeover**
`com.google.android.apps.tv.launcherx` (stock Google TV home),
`com.google.android.tungsten.setupwraith` (setup wizard; only after checking it owned no Wi-Fi network)

Kept on purpose: YouTube, Netflix, Prime Video, JioHotstar, the Play Store, Google Play services,
Chromecast built-in, Google Assistant and the system update service.

## Never disable on this TV

- Live TV, HDMI and tuners: `com.android.tv`, `com.realtek.dtv`, `com.realtek.tv.passthrough`,
  `com.realtek.tv.atv`, `com.realtek.providers.tv`, `com.realtek.tvmediaplay`
- Remote, Bluetooth and voice: `com.android.bluetooth`, `com.google.android.tv.remote.service`,
  `com.sony.dtv.remotecontrolsettings`, `com.google.android.katniss` (Assistant / voice search)
- Keyboards: `com.google.android.inputmethod.latin`, `com.sony.dtv.ime.chww`
- System glue: `com.sony.dtv.sonysystemservice`, `com.sony.dtv.m2.devicecontrolservice`,
  `com.sony.dtv.provider.modelvariation`, `com.sony.dtv.applicationdisabler`
- `com.android.vending` (Play Store), `com.google.android.gms` (Play services)
- Settings: `com.android.tv.settings`, `com.realtek.advancesetting` (both also provide the
  `FallbackHome` activities, the safety net if the launcher crashes)
- `com.realtek.usbupdate` (firmware update from USB), unless you are sure you will never need it
- `com.sony.dtv.ipcontrol` (network remote control; hidden in the launcher instead)

## Quirks

- **Packages Sony re-enables at every boot.** `com.sony.dtv.ecodashboard` and the three
  `com.sony.dtv.m2.seeds.*` packages come back after each reboot, most likely from
  `com.sony.dtv.applicationdisabler`'s boot receiver. Record them on the `# firmware-reenables:` line of
  `state/disabled.txt` so `reapply.sh` skips them, and add the visible ones to `hidden_packages`.
- **`set-home-activity` is a no-op** while the stock launcher is enabled: it prints Success and HOME
  still opens Google TV. Disabling `launcherx` then hands HOME to setupwraith's recovery screen; with
  setupwraith also disabled, HOME goes to our launcher.
- **Wi-Fi ownership.** Every saved network on this set was owned by `android.uid.system:1000`, so
  disabling setupwraith was safe. Check yours (`dumpsys wifi | grep -o 'cname=[^ ]*'`) first.
- **Second Android user.** This TV had a second user profile; `pm list packages` without `--user 0`
  mixed its state in. All scripts pass `--user 0`.
- **Live TV and HDMI screenshots are blank** (content protection). Use `topResumedActivity` instead.
- **`com.realtek.servicemenu` and other factory/service menus: never open them**, not even to look.
- **The Inputs list** from the TV input framework showed HDMI 1, HDMI 2, HDMI 3 (eARC/ARC), and the
  antenna and satellite tuners. There is no composite AV input on this model.
