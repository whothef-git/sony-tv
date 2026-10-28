# Phase 2.3 — GO DAIKIN fan, mode and separately approved power validation

Date: 2026-10-28, Asia/Singapore. Stable launcher remains Octahedron v2.0 (version code 11).
The separate developer package is `tv.widgetv.godaikinpoc`, version `0.3-control-validation` (code 3).
No production Daikin controls or new production launcher UI are enabled.

## Scope and compatibility

Reuses Phase 2.1 Cognito/cloud authentication, discovery, HTTPS transport and Android Keystore
storage, plus Phase 2.2 stable-ID binding, timestamp/version parsing and restoration safeguards.
Phase 2.3 requires the already encrypted pinned cloud device ID; it has **no name/IP bootstrap**.
All targeted reads and exact-payload writes must match that ID. The other four ACs remain read-only.
The installed stable launcher, Smart Home UI, Tapo and KP303 code/configuration are untouched.

The user confirmed TV AC's official GO DAIKIN controls show Cool/Fan/Dry and three manual fan
speeds. Dining has five speeds and is outside this phase's write scope. Mappings follow the
unofficial reference: fan Low=2/Medium=4/High=8; modes Cool=1/Fan=2/Dry=4. An already-reported
Auto fan value 128 can be restored if it was the original setting; no Auto/Heat operating mode
or five-speed fan value is enabled. Supported modes were not inferred merely from the model name.

Source references:
[API](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/api.py),
[types](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/types.py).
The source is Apache-2.0 licensed and attribution is retained. The API remains unofficial and
has no established third-party authorization, stability guarantee or published safe quota.

Read-only TV AC result before tests: Cool, Low fan, 23 C, ON, connected, timestamp
2026-10-28 18:06:47, state version 116687. Reported capability flags were
`Bar_CoolM=0`, `Bar_FanM=0`, `Bar_DryM=0`, `Bar_AutoM=1`, `Bar_HeatM=1`, `Bar_AutoF=0`,
`Bar_LowF=0`. Their meaning is displayed without claiming official documentation; the user's
visible selectable controls supplied the independent support check. Indoor RPM reported 0
despite power ON, so that telemetry is not treated as proof of physical fan behavior.

## Test and confirmation procedure

The developer screen provides Read, Prepare FAN, Prepare MODE, and Prepare separate POWER.
Prepare sends reads only and waits, bounded to 40 polls at 15-second intervals, for fresh
connected state **before** recording the original and showing the command dialog. A cloud report
must be at most 120 seconds old / 5 seconds in the future, have a positive state version,
and contain recognized original power/mode/fan/temperature fields. Date parsing is strict using
Asia/Kuala_Lumpur; timezone interpretation remains a Malaysia-account assumption.

The original settings and phase are encrypted and committed before the approval dialog.
Each test has its own dialog defaulting focus to Cancel. There are no startup writes, intent
approval flags, automatic cross-test execution, or write retries after ambiguous timeouts.
A review expires in 15 minutes. After approval, fresh unchanged original state is checked again;
if freshness expired while the user reviewed it, only read polling occurs until it is fresh.
Changes to original settings require a new preview/approval.

| Test | Proposed change | Approved restoration | Physical observation hold |
| --- | --- | --- | --- |
| Fan | One supported manual change: normally Low to High | Original fan only | 45 seconds |
| Mode | Normally Cool to supported Fan mode | Original mode, fan and target temperature | 180 seconds |
| Power | Separate explicit OFF approval, original must be ON | Original ON, mode, fan and temperature | 180 seconds |

The reference's mode helper also sends `Set_OnOff=1`; that field is deliberately omitted from
fan and mode tests. Their transport permits reject any power field, even an unchanged ON value.
Only the separate POWER transaction can permit ON/OFF. Mode-dependent fan/temperature changes
are allowed during the changed-mode stage; the explicit original-setting restoration bundle
must bring all four tracked original settings back. No unapproved preset/swing controls are added.

The three-minute observation periods allow time to observe the unit and avoid rapid back-to-back
mode/power cycling. Daikin describes a normal protection delay of about three minutes after a stop
or mode change, so a delayed compressor restart alone is not considered command failure:
[Daikin Malaysia troubleshooting](https://www.daikin.com.my/service/troubleshooting/).
The unit may also continue fan activity for built-in cleaning/mould-proof behavior; cloud OFF
and physical fan stoppage must not be assumed equivalent.

## Response verification and recovery

- Every real state read cross-checks the same stable ID's registered-device and targeted-state
  responses for power, mode, fan and temperature. Transient disagreement is unverified state.
- Commands are serialized. The transport consumes a single-use exact-ID/exact-payload permit
  before connecting; arbitrary keys, targets, extra fields and unsupported values are denied.
- HTTP success/acknowledgement alone never proves success. Two subsequent consistent fresh
  observations must match the expected fields with both a newer version and later timestamp.
- After changed-state confirmation, the screen holds for physical observation, then submits
  the approved original-setting compensation once and verifies fresh restoration twice.
- If a write times out, reads may confirm its actual effect; it is not blindly resent.
- A confirmed change still triggers its one approved compensation if observation is interrupted.
- An unconfirmed change or restore keeps the encrypted journal pending and blocks another test.
  Recovery is a separate explicit approval and can only restore the recorded payload for the pinned ID.
- Session expiry uses the existing refresh flow. Rejected authentication stops until a new
  explicit action. Offline/stale/malformed/mismatched state never becomes mock success.
- Process restart sends no recovery command automatically. Killing the app/TV mid-test can require
  explicit recovery; no foreground service or external recovery backend is introduced.
- Cloud-reported success and user-observed physical behavior are recorded separately. Neither
  is a measurement of room cooling performance or proof of every possible device feature.

Credentials, pinned ID and original-setting journal stay encrypted in the existing app-private
Keystore vault. Tokens/keys are transient; logs/results/archives exclude actual cloud IDs,
account email/password, tokens, device keys and raw cloud responses. Platform TLS/hostname
verification remains enabled and cleartext/redirects remain blocked. This is a trusted debug/ADB
artifact, not a production security or service-authorization claim.

## Build and automated tests

Java 17 / Android SDK 34 / Android Gradle plugin 8.5.2, no new runtime dependency.
From `launcher/`:

```powershell
.\gradlew.bat -p godaikin-poc --offline --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
```

Host tests: 75 cloud-client checks, 75 Phase 2.2 transaction checks and **84 new Phase 2.3 checks**
passed. Sony TV instrumentation passed **242 checks** (the same 234 synthetic checks plus eight
actual Android Keystore/persistence checks using isolated synthetic credentials).
These tests never contact the real cloud or write to a real AC. They cover exact permits,
separate power consent, unsupported modes/speeds, freshness before preview, original persistence,
expired/used approval, normal changes/restoration, mode-dependent settings, rejection, ambiguous
timeout, stale desired echoes, offline/wrong devices, failed compensation and explicit recovery.

Debug and instrumentation APK builds succeeded. Android lint completed with zero errors and
16 developer configuration/text warnings. Release variant remains disabled.

Open the separate screen (never install over `tv.widgetv.home`):

```text
adb install -r godaikin-poc/build/outputs/apk/debug/godaikin-read-only-poc-debug.apk
adb shell am start -n tv.widgetv.godaikinpoc/tv.widgetv.home.godaikin.ControlTestActivity
```

Sanitized current progress: `files/control-validation-result.txt` under `run-as tv.widgetv.godaikinpoc`.
Completed per-test results: `phase23-fan-result.txt`, `phase23-mode-result.txt`, `phase23-power-result.txt`.
No control can be approved by an intent argument or this diagnostic file access.

## Source changes and rollback

New in `launcher/godaikin-poc/`: `ControlCommandGate.java`, `ControlValidation.java`,
`ControlTestActivity.java` and `checks/.../ControlChecks.java`.
Modified in that isolated module: client (capability parsing/scoped desired payload), HTTPS transport
(gate interface), existing temperature gate (same interface), mock runner, manifest/developer label,
debug version, README and NOTICE. This report is new. Existing production launcher source is unchanged.

Workspace rollback: stable v2.0 under `backups/phase2.1-baseline/`; Phase 2.2 debug APK/source under
`backups/phase2.3-baseline/`. The separate package retains the working account securely across updates.
Do not remove it during an unfinished test; resolve its original-setting restoration first.
No stable APK, TP-Link configuration or launcher UI is overwritten by this experiment.

## Real results

Recorded below only after separate user confirmations and fresh readback. Physical observations
will be identified explicitly as user reports, not inferred from status flags or zero RPM telemetry.
Production Daikin controls remain disabled until the user explicitly approves a later integration.

### Fan — cloud change and restoration verified

The user separately confirmed the FAN test on the TV. One `Set_Fan:8` command was sent, followed
by a 45-second physical-observation hold and one `Set_Fan:2` compensation. Both transitions
passed the two-observation fresh timestamp/version checks. No power or mode field was sent.

| Stage | Power | Mode | Fan | Target | Reported timestamp | Version |
| --- | --- | --- | --- | --- | --- | --- |
| Fresh recorded original | ON | Cool | Low | 23 C | 2026-10-28 18:16:47 | 116688 |
| Changed, confirmed | ON | Cool | High | 23 C | 2026-10-28 18:17:36 | 116690 |
| Original restored, confirmed | ON | Cool | Low | 23 C | 2026-10-28 18:18:36 | 116692 |

Sanitized evidence is retained in workspace `diagnostics/godaikin-phase23-fan-result.txt`.
The user separately reported: **“Stronger airflow, then returned to Low.”**
Therefore fan control/restoration is verified through fresh cloud responses and corroborated
by the user's physical observation. Airflow/noise was not independently instrumented.

### Mode — cloud change/restoration and physical behavior verified

A fresh original snapshot (18:18:36, version 116692) was recorded before the dialog. The user
separately confirmed `{"Set_Mode":2}` and the displayed compensation
`{"Set_Mode":1,"Set_Fan":2,"Set_Temp":23}`. Because that snapshot aged during review, the
approved transaction waited using reads only until fresh unchanged original state was available.

| Stage | Power | Mode | Fan | Target | Reported timestamp | Version |
| --- | --- | --- | --- | --- | --- | --- |
| Fresh original immediately before the command | ON | Cool | Low | 23 C | 2026-10-28 18:26:46 | 116693 |
| Changed, confirmed | ON | Fan | Low | 23 C | 2026-10-28 18:26:51 | 116696 |
| Original restored, confirmed | ON | Cool | Low | 23 C | 2026-10-28 18:30:14 | 116699 |

One mode change and one restoration bundle were sent. Both stages passed the two-observation
fresh readback checks. The three-minute Fan-mode hold completed before restoration. No power
field was sent. Sanitized evidence: workspace `diagnostics/godaikin-phase23-mode-result.txt`.

The user reported Fan-only behavior during the test. A follow-up question after the verified
18:30:14 restoration explicitly confirmed **“Cooling has returned after restoration.”**
Therefore mode change/restoration has fresh cloud evidence and user-reported physical corroboration.
No independent compressor, airflow or room-temperature instrumentation was used.

### Power — explicitly skipped by the user

The explicit power-off/restore-on test is implemented and mock-tested. When offered the separate
power review, the user selected **“Skip power testing.”** No power review was prepared or approved,
and **no power ON/OFF command was sent in Phase 2.3**. Real power behavior remains unverified.

### Artifacts

Workspace debug APK: `GO-DAIKIN-Phase2.3-control-validation-debug.apk`, 111,659 bytes.
SHA-256: `acb8253e2484c73e66eaec93c1de5326b9d2dd2e0684c02f1c0e1ca10af2ed6c`.
The final APK label is GO DAIKIN Developer Tests; the executable control logic is the same code
used in the successful fan and mode tests. After verified restoration, this final APK was installed
and its on-TV SHA-256 matched the artifact above. No stable v2.0 APK is replaced.

The mock instrumentation package was removed and the TV returned to the existing launcher.
The installed stable v2.0 APK SHA-256 still equals
`5934594925f42719d30435ac89483f4bb307d4357b625088b345be06b1020b44`.
All baseline production launcher sources and app/parent build settings matched their original
SHA-256 hashes (zero differences). Tapo/KP303 code and configuration were not modified or switched.
They were not subjected to new physical control tests during this phase.

### Remaining limitations and production stop

- Real validation covers Low/High fan and Cool/Fan mode on the pinned TV AC/account/firmware only.
  Medium/Auto fan, Dry and other unit capabilities were not exercised. Dining's five-speed control
  and all other ACs remain outside write scope.
- Power has only mock validation and was deliberately skipped on hardware.
- Physical corroboration is the user's report, not independent instrumented measurement.
- Only the four tracked original fields (power, mode, fan, target temperature) were verified;
  presets, swing, timers, cleaning and other settings are not claimed validated.
- Invalid credentials, offline events, actual cloud timeouts, token expiry and process death
  were not deliberately induced on the real AC. Those behaviors have synthetic test coverage only.
- Normal compressor/cleaning/protection timing can differ from the cloud's instantaneous setting.
- The unofficial API can change; the POC is not production integration or vendor authorization.
- **Production Daikin controls remain disabled. Explicit user approval of a later integration
  is still required.** No release APK or main Smart Home control UI was produced.
