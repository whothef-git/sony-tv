# Firmware updates

Short version: leave automatic updates on. If the old home screen ever comes back, turn debugging on,
run `tools/reapply.sh`, and turn debugging off again. It takes about two minutes.

## What a normal update leaves alone

Monthly security patches and small vendor updates usually change nothing you will notice.

- **The launcher stays installed.** It lives in user data like any app you installed yourself. It
  targets Android 14 (API 34), and Android 15 runs it unchanged.
- **Disabled apps stay disabled.** "Disabled for user 0" is stored in user data, not in the system
  image, so an update does not reset it. The Play Store also skips disabled apps when it updates
  others, so they don't quietly come back that way.
- **Your settings stay.** Private DNS, the screen-off timeout, and everything in the launcher's Prayer
  settings panel (method, Asr school, offsets, clock font, text size) are kept.

## What a big update can undo

A major Android upgrade (for example 14 to 15) or a large vendor release rebuilds more of the system:

- **The stock Google TV home can be re-enabled.** It has a higher HOME priority than any sideloaded
  launcher, so the TV boots to it, ads and all.
- **Some vendor apps can be re-enabled**, especially ones the update replaced with a new version.
- **Some vendors re-enable a few packages at every boot** regardless of updates (Sony does; see
  `examples/sony-bravia-vu31.md`). Those are listed on the `# firmware-reenables:` line of
  `state/disabled.txt` and hidden in the launcher instead of fought with.
- **A factory reset undoes everything**: the launcher is gone and every app is enabled again. Run the
  whole `PROMPT.md` flow again; your old `state/choices.md` makes it quick.

The worst case is mild: you get the stock home screen with its rows and ads. HDMI inputs, Live TV, your
apps and the remote all keep working.

## The fix: `tools/reapply.sh`

1. On the TV: Settings > System > Developer options. Turn on **USB debugging** and **Network debugging**
   (the name varies: "ADB debugging", "Network/ADB debugging"). If Developer options is gone, open
   Settings > System > About and press OK on **Build** seven times.
2. On your computer, in this repo:

   ```bash
   TV_IP=<tv address> tools/reapply.sh
   ```

   Accept "Allow USB debugging?" on the TV if it asks. The script:
   - reads `state/disabled.txt` and compares it with what is disabled on the TV now;
   - re-disables anything the update turned back on, one package at a time;
   - skips the `# firmware-reenables:` packages and anything no longer installed;
   - refuses to disable the setup wizard (setupwraith) if it owns a saved Wi-Fi network, because that
     would drop the TV off Wi-Fi, and tells you how to fix it first;
   - makes the launcher HOME again, presses HOME and checks which app has focus;
   - prints `re-disabled N, already fine M, ...` and `home: OK` (or `NOT OK` with a pointer).

   Running it twice is harmless; the second run re-disables nothing.
3. If the launcher itself is missing (after a factory reset), run `tools/build-install.sh` first.
4. Close ADB again (see "Close ADB" in `PROMPT.md`), then check:

   ```bash
   python3 tools/adb-closed.py <tv address>     # must print CLOSED (connection refused)
   ```

## Recommendation

Keep automatic system updates **on**. Security patches matter more on a device that sits on your home
network all day than a home screen does. Treat "the old home screen is back" as the signal to run
`tools/reapply.sh`, not as a reason to block updates.
