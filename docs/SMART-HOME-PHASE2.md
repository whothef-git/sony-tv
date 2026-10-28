# Octahedron / Glass TV 2.0 — Phase 2 delivery

Version 2.0 is built and installed in the existing launcher. Real mode is the default. This delivery has **verified KP303 control and one P110's authenticated live readings**, with deliberately disabled GO DAIKIN controls. Do not describe all integrations as hardware-complete.

## Integration status and supported controls

| Device | Software implementation | Hardware verification |
|---|---|---|
| Kasa KP303 | LAN discovery, TCP IOT transport, actual outlet IDs, independent Lampu Tidur / iPhone Charger / Laptop controls, response and readback confirmation | Real discovery, reads and six state-change/restore checks passed on the user's KP303. Initial OFF / ON / OFF restored. |
| Tapo P110 ×2 | Manual IPs and discovery, KLAP v1/v2 and legacy AES login variants, encrypted account setup, power and watts/daily kWh parsing, reconnection | After third-party compatibility was enabled, Fan TV was discovered on the TV LAN, authenticated using KLAP2, and reported real ON state, 44 W and 0.01 kWh today. Those values are a snapshot. No P110 power command has been tested, and the second plug remains unverified. |
| GO DAIKIN ×5 | Five named disabled setup entries remain in Manage Devices | Controller models and compatible API are not verified. No local/cloud login, status or control is claimed. |

All device communication runs from the Android application. No external backend, Raspberry Pi, Home Assistant, Python service, root or listening control server is required. See [protocol research and security](SMART-HOME-PROTOCOLS.md) for the independent Java port and unsupported firmware paths.

## User-facing behavior

- Existing Home, Prayer Time, Weather, Smart Home, clock, local weather, Islamic date, background, system controls, tab positions and remote navigation remain.
- Saudi information and application launcher are hidden only inside Smart Home.
- Real and developer demo repositories are separate; failures never select demo mode.
- Manage Devices: MENU, or hold OK on the Smart Home tab. Supports scanning, manual IP/protocol setup, rename, room, order, status/errors, refresh, removal and private TP-Link account entry. Removed identities are suppressed from automatic re-addition; manual addition can restore them.
- Credential entry is FLAG_SECURE. A non-secret indicator confirms whether an encrypted account profile is saved. Passwords are never shown in status, source, logs or exported metadata.
- Polling approximately every 10 seconds and rediscovery every 30 seconds while real Smart Home is active. Work stops when hidden/paused, with no foreground service.
- Connectivity and power are independent. Online + OFF stays visible; loss shows connecting, then hides after a 45-second grace. Reconnection preserves device identity/order and existing focus recovery/reflow.
- Commands show pending state and suppress repeats. Actual response/readback is required; timeout does not resend the command or assume success. Missing readings stay unknown. KP303 has no energy display.
- Registry metadata persists separately from Keystore-encrypted credentials. Sessions/tokens are memory-only, and cached states are not restored as ONLINE after app restart.

## Files created

Unless noted, Java files are in `launcher/app/src/main/java/tv/widgetv/home/`:

| File | Purpose |
|---|---|
| `DeviceRegistry.java` | Durable identity, names, rooms, IP/protocol, order, cached state and removal suppression |
| `SmartCredentialStore.java` | Android Keystore AES-GCM encrypted account storage |
| `SmartDeviceDriver.java` | Injectable transport boundary |
| `DeviceDiscoveryManager.java` | Bounded network-bound broadcast/unicast discovery |
| `TpLinkDiscoveryCrypto.java` | Modern RSA/OAEP + AES discovery envelope |
| `TpLinkCrypto.java` | KLAP/AES/XOR wire primitives using JCA |
| `TpLinkSession.java` | KLAP and legacy AES authenticated sessions |
| `LanDeviceHttp.java` | Private-IP HTTP with timeouts and disabled redirects |
| `TpLinkState.java` | Validated identity/state/energy parsing and outlet request targeting |
| `TpLinkDeviceClient.java` | Direct LAN device reads, commands and confirmation |
| `RealSmartHomeRepository.java` | Lifecycle, persistence, polling, discovery and asynchronous command coordination |
| `DeviceManagementDialog.java` | Native remote-navigable setup and management |
| `launcher/app/src/main/res/xml/local_network_security.xml` | Required local HTTP policy; system TLS verification unchanged |
| `launcher/app/src/androidTest/java/tv/widgetv/home/SmartHomeIntegrationChecks.java` | Isolated Android storage/lifecycle mocks |
| `launcher/protocol-tests/SmartProtocolChecks.java` | Host protocol/state mock peers |
| `launcher/protocol-tests/README.md` | Reproduction instructions |
| `docs/SMART-HOME-PROTOCOLS.md` | Protocol research, compatibility and security limits |
| `docs/SMART-HOME-PHASE2.md` | This report |

## Files modified

- `SmartDevice.java`: persisted metadata, stable identity, pending/errors, outlet IDs/names and demo marker.
- `SmartHomeRepository.java`: lifecycle and mode boundary.
- `DemoSmartHomeRepository.java`: explicit demo labeling, retained original behavior.
- `SmartHomeView.java`: real default, explicit demo switch, management entry and lifecycle integration.
- `DeviceCardAdapter.java`: identity-based stable RecyclerView IDs, real/cached/unknown/pending values and outlet names.
- `DeviceControlDialog.java`: real controls, pending/error feedback, actual readings and duplicate-command prevention.
- `MainActivity.java`: management MENU/long-OK entry and repository shutdown.
- `launcher/app/src/main/AndroidManifest.xml`: local-network security resource.
- `launcher/app/build.gradle`: version 2.0 / versionCode 11 and native instrumentation runner.
- `README.md`: real-mode setup and limitations.

ScreenTabs, SmartHomeStyle and activity_main.xml did not need Phase 2 redesign. No dependency/framework or additional Android permission was added. Local packaging scripts/reports outside the repository were also updated.

## Automated tests versus hardware tests

| Test group | Result | What it establishes |
|---|---|---|
| Host protocol/state synthetic peers | 70 passed | KLAP v1/v2 vectors/challenges/signatures, AES login variants, tampering, invalid credentials, ambiguous timeout without resend, state/units, actual outlet IDs/reordering, encrypted discovery CRC/RSA/AES |
| Android mock integration harness | 40 passed | Real Keystore and isolated persistence, mock discovery/driver coordination, pending/repeat suppression, online OFF, grace/reconnect, changed IPs, restart, sleep/wake lifecycle, network loss/recovery, metadata, removal suppression and duplicate manual IP rejection |
| Preserved Phase 1 model checks | 40 passed | Existing developer demo functionality |
| Existing launcher on-TV regression | 16 passed | Existing Home/Prayer/Weather/navigation and dates remain functional |
| Real KP303 | 6 passed | Each physical outlet changed state and returned to its initial confirmed state using the TV application |
| P110 hardware | One authenticated read path verified | Fan TV returned actual state and power/energy readings over KLAP2; switching and the second plug remain unverified |
| Daikin hardware | Not verified / disabled | Do not infer functionality from mock results |

The optimized release and debug/test builds compiled successfully; release vital lint passed. Full lint was not rerun; the earlier unrelated QUERY_ALL_PACKAGES policy finding remains outside this change. A final local release update added only the visible saved-account indicator, which was checked on the TV and confirmed encrypted storage. The temporary instrumentation APK was removed, and the optimized release remains installed.

External local evidence is in `diagnostics/phase2-protocol-checks.txt`, `phase2-android-mock-checks.txt`, `phase2-demo-regression.txt`, `phase2-existing-ui-checks.txt`, `phase2-kasa-hardware-checks.txt`, and non-secret UI status snapshots. No credential-entry snapshots were taken.

## Remaining hardware work

Fan TV's discovery, KLAP2 authentication and actual readings are verified after enabling third-party compatibility. Still verify both plugs' ON/OFF commands, the second plug's authentication/readings, and OFF-but-online visibility. The saved account indicator by itself confirms storage only; successful device responses are required to verify authentication.

Physical unplug/replug, actual router/Wi-Fi interruption, DHCP address changes and prolonged TV sleep/wake have not been physically exercised for all real devices; those cases currently have mock coverage. Verify outlet-to-appliance labels against physical wiring. GO DAIKIN requires controller/indoor-unit model information and a compatible documented method before enabling controls.

Other limits: IPv4-only local device addressing; no TPAP, HTTPS-only local transport or unverified SMART child APIs. Legacy Kasa XOR is not encrypted. The UI supports one shared TP-Link account profile, with credentials kept separate from metadata. Long-run stress/performance testing and other Sony/Google TV versions remain unverified.

## Build, install and rollback

Use JDK 17 and Android SDK 34 in `launcher/`:

```text
gradlew.bat --offline --no-daemon assembleRelease
```

APK: `launcher/app/build/outputs/apk/release/app-release.apk`. The local exported copy is `Octahedron-2.0-smart-home.apk` (also `WidgeTV-2.0-optimized.apk`), approximately 1.11 MB. The source archive is `WidgeTV-2.0-source.zip`.

The local APK is already installed on the connected TV. For a later installation, use the TV's current IP and preserve application data:

```text
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r Octahedron-2.0-smart-home.apk
adb -s <TV_IP>:5555 shell am start -n tv.widgetv.home/.MainActivity
```

The existing local signing identity is retained for in-place updates. A different machine must use the same private signing identity; the source's debug signing is for local development, not public distribution.

Stable Phase 1 APK/source were preserved before changes in `backups/launcher-before-phase2.apk` and `backups/source-before-phase2.zip`. Android may reject a lower-version non-debuggable APK; if rollback is needed, rebuild the preserved Phase 1 source with a higher versionCode and the same signing key rather than uninstalling or clearing user data. No app-data deletion or credential export was performed. GitHub was not changed by this phase.
