# GO DAIKIN read-only Android proof of concept

**Current: Phase 2.3, `0.3-control-validation`, separate developer APK.** See
[fan/mode/separately approved power validation](../../docs/GO-DAIKIN-PHASE2.3.md).
Production launcher controls remain disabled. Earlier sections below describe their historical phases.

**Current development version: Phase 2.2, `0.2-temperature-validation`.** The original read-only
screen and its default-deny transport remain available. A separate developer temperature-validation
screen now supports the explicitly confirmed, stable-ID-scoped TV AC test. See
[Phase 2.2](../../docs/GO-DAIKIN-PHASE2.2.md) for its safety gates, tests and actual hardware status.
The sections below describe the historical Phase 2.1 baseline; their no-control statements apply
to that baseline and the retained read-only screen, not the newly approved validation screen.

Phase 2.1 experiment in the existing Octahedron repository. Its reusable Java client is
isolated from the stable launcher's runtime. A temporary developer-only Android test
package runs that client directly on the Sony TV. It uses no Home Assistant, Python,
backend, proxy, Raspberry Pi, AWS access key, or separate computer at runtime.

**Real cloud login, discovery of five registered ACs and embedded status retrieval were
verified on the Sony TV on 2026-10-28. AC controls were not implemented or tested.**

See [the feasibility report](../../docs/GO-DAIKIN-PHASE2.1.md) for evidence and limitations.

## Isolation and safety

- Separate package `tv.widgetv.godaikinpoc`; no HOME or launcher intent filter.
- No change to `app/`, parent Gradle settings, Smart Home UI or TP-Link integrations.
- No production/release build variant. The normal launcher build does not include this module.
- Client API exposes only login, list/status read and session close.
- HTTPS transport allowlists exact authentication and device-list endpoints and payload shapes.
- Does not call `publishdevicestate`, including the upstream empty desired-state "read" preamble.
- No AC writes, polling loop, service, automatic credential retry or demo fallback.
- Normal Android TLS/hostname verification; redirects and cleartext blocked.
- Separate Keystore AES-256-GCM encrypted account, no TP-Link credential fallback.
- ID/refresh tokens stay in memory and clear on client close; no token persistence.
- Screen capture, view state saving and autofill are disabled on the private credential form.
- Summary contains whitelisted device status only. Never pull preferences or capture the login form.
- Debug builds are ADB-inspectable on an authorized device. Keep ADB trusted; do not distribute this test APK.
- Java/JSON strings cannot be reliably erased from managed memory. Temporary byte arrays are zeroed where practical.

## Build and run (debug only)

Requires the existing JDK 17, Android SDK 34 and cached Android Gradle plugin 8.5.2.
From `launcher/`, with `ANDROID_HOME` set to the SDK:

```powershell
.\gradlew.bat -p godaikin-poc --offline --no-daemon assembleDebug assembleDebugAndroidTest
```

Install **only the separate test package**, never replace `tv.widgetv.home`:

```text
adb install -r godaikin-poc/build/outputs/apk/debug/godaikin-read-only-poc-debug.apk
adb shell am start -n tv.widgetv.godaikinpoc/tv.widgetv.home.godaikin.CloudTestActivity
```

Enter the GO DAIKIN account privately on TV and press **Save securely and test — reads only**.
Both fields blank uses the already saved GO DAIKIN test account. Partial input is rejected.
The test performs one password authentication and one registered-device/status request.
The reusable client can refresh an expiring token; that path is mock-verified only.
No other cloud operation is possible through its transport.

BACK returns to Octahedron. To remove saved credentials use **Forget GO DAIKIN test credentials**.
Uninstalling `tv.widgetv.godaikinpoc` removes its private app data without touching the launcher.

Sanitized result only (never read credential preferences):

```text
adb shell run-as tv.widgetv.godaikinpoc cat files/read-only-result.txt
```

## Automated tests

`checks/.../CloudClientChecks.java`: 75 synthetic protocol checks, no network. The same checks
run on JVM and actual Android. Includes auth payloads, ID-token authorization, refresh,
expired session rejection, challenges/MFA stop, invalid credentials, rate limiting, timeout,
TLS error handling, missing/malformed data, unknown values, cloud ID deduplication, status
privacy and transport rejection of desired-state and non-allowlisted requests.

`src/androidTest/.../CloudMockChecks.java`: those 75 checks plus four actual Android Keystore
and persistence checks, using isolated preferences/key aliases and synthetic credentials.
It never reads or modifies the real account. This is not a real cloud/hardware test.

```text
adb install -r godaikin-poc/build/outputs/apk/androidTest/debug/godaikin-read-only-poc-debug-androidTest.apk
adb shell am instrument -w tv.widgetv.godaikinpoc.test/tv.widgetv.home.godaikin.CloudMockChecks
adb uninstall tv.widgetv.godaikinpoc.test
```

Host tests require Java 17 and test-only `org.json:json:20240303` (not an APK dependency).
Compile `GoDaikinCloudClient.java`, `CloudHttpsTransport.java` and `CloudClientChecks.java`
against that jar into a temporary directory; run `tv.widgetv.home.godaikin.CloudClientChecks`.

Successful debug APK: `build/outputs/apk/debug/godaikin-read-only-poc-debug.apk` (38,637 bytes).
No production APK was built or installed during Phase 2.1.
