# Phase 2.2 — GO DAIKIN temperature control validation

Date: 2026-10-28. Baseline: stable Octahedron v2.0 and the working Phase 2.1 cloud POC.
Test target: user-identified TV AC, FTKH35BV1MF, cloud firmware V1.2.5.
This is an isolated developer experiment. No production Daikin controls are enabled.

## Implementation

Reuses the authenticated Phase 2.1 Java client, device listing, platform HTTPS and Keystore vault.
No Home Assistant or external runtime/backend. No changes to the launcher app, Smart Home UI,
Tapo/KP303 integration or stable APK. The same separate debug POC package is updated in place,
preserving private test credentials; it does not replace `tv.widgetv.home`.

Reviewed the reference's
[`api.py`](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/api.py) and
[`climate.py`](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/climate.py).
The Apache-2.0 attribution/license remains bundled. This is an unofficial protocol; no Daikin
approval, API stability guarantee or published safe request quota has been established.

Only the temperature payload is implemented:

```json
{"requestData":{"type":3,"username":"<private>","thingName":"<private pinned cloud ID>","key":"<private transient key>","payload":{"state":{"desired":{"Set_Temp":24}}}}}
```

It posts to the same reference `publishdevicestate` endpoint, with the existing ID-token
authorization. The only variable desired field permitted is integer `Set_Temp`, 16–31 C.
Power, modes, fan, swing, LED, presets, empty desired-state writes and other targets are blocked.
No write is sent by startup, discovery, Read, Prepare, tests, timer or payload review.

Targeted reads use reference `publishdevicestate` type 1 without its empty desired-write preamble.
Each real validation read also retrieves the registered-device list and checks this same stable
ID's target temperature and power against the targeted reply. The targeted reply must identify
the requested device. Actual replies contain a flat shadow with `Set_Temp`, `updatedOn`,
`shadowStateVersion`, `thingName` and `eventType`.

## Identity and confirmation

First binding selects exactly one previously verified cloud registration using the known TV name,
IP and firmware together. The returned stable cloud ID is then checked against the targeted read.
On user confirmation that ID is pinned in the encrypted vault. Subsequent sessions select only
the pinned ID, independent of name/IP changes. The transport restricts every targeted read/write
to that ID. No actual cloud ID/key is embedded in code, logged or shown in the UI.

The developer screen initially offers only Read, Prepare 1 C and Review payload. Explicit recovery
review appears only when a pending journal is detected after an action. Prepare reads state and creates a 15-minute plan; it sends nothing to the AC. A cached connected snapshot can be used only for this preview. Review
shows the original target, exact temperature field, test target and restoration. The confirmation
dialog defaults focus to **Cancel**. Only the user's **Confirm test and restore** activation
authorizes the first write and its compensating restoration. Intents provide no approval flags.

The approved transaction:

1. Rechecks plan age (15 minutes). After on-screen approval, it may poll for up to ten minutes
   for fresh connected original state. The temperature/power/mode/fan must still match the exact
   preview; the payload is never silently regenerated. Age is rechecked after network reads.
2. Saves the original settings and pending transaction durably in an AES-GCM encrypted journal.
3. Sends one temperature change of +1 C (or -1 C at the 31 C upper bound).
4. Reads back until two consistent observations have the changed target, unchanged power/mode/fan,
   a newer state version and a later reported timestamp.
5. Sends one compensation command restoring the recorded original temperature.
6. Requires two further consistent fresh observations with the original temperature and newer
   version/timestamp before marking restoration complete.

The transport consumes a single-use exact-temperature permit **before** opening its connection.
No blind retry occurs after an ambiguous write. A response/HTTP 200 cannot establish success.
Explicit rejection fields are parsed; all acknowledgement bodies remain unverified until readback.

## Freshness and failures

- Preparation requires a connected, powered-on AC in Cool mode, known integer target, known fan,
  positive state version and valid timestamp. It may preview cached state, clearly labeled.
  **Before any write**, the original must be no more than 120 seconds old / 5 seconds in the future.
  No freshness threshold was increased. A confirmed action can wait with read-only polls for a new
  report; startup, an unconfirmed plan and an expired plan cannot start this waiting transaction.
- Date parsing is strict, explicitly using Asia/Kuala_Lumpur. The actual Sony TV clock matched
  local time. Cloud timezone interpretation remains a Malaysia-account assumption, not official API documentation.
- Both a newer timestamp and a newer version are required; desired-value echoes without those
  signals never count as success. This is cloud-reported device confirmation, not an independent
  measurement of the physical unit or room cooling performance.
- At most eight read attempts per stage, seven seconds apart. Two consistent observations are
  required. Read failures may be polled; writes are not repeated automatically.
- Expired tokens reuse the existing refresh flow. Rejected sessions stop; a new explicit action
  is needed for reauthentication. No demo fallback.
- Offline state, changed identity/settings, malformed responses, stale reports, rejection,
  network failure and timeout keep the test unverified or pending recovery.
- If the changed state is confirmed despite an ambiguous acknowledgement, compensation is still
  attempted once. If restoration cannot be confirmed, the encrypted journal blocks another test.
- Process restart never triggers a restore write. A separate recovery dialog requires approval,
  reads current state first and can issue only the recorded original temperature to the pinned ID.
- Original target/power/mode/fan/time/version and pending phase are encrypted. Account and
  binding/journal use distinct authenticated-data labels. Tokens and device keys are RAM-only.
- The debug package is a trusted ADB/development artifact, not production security. No credential
  or raw cloud-response capture is needed to inspect its sanitized result file.

## Observed read-only hardware checks before write approval

The targeted cloud read succeeded for the verified TV registration. It returned ON, 23 C and
state version 116679 with timestamp 2026-10-28 17:26:47. Preparation was correctly blocked after
that snapshot became older than two minutes. Refreshing the official app did not immediately
advance that snapshot. A later read returned matching device-list and targeted snapshots at
17:36:47, version 116680, still 23 C/ON. Observed timestamps suggest roughly ten-minute updates
while settings are unchanged; this is an observation, not a guaranteed service interval.

No freshness threshold was relaxed to get a command through. Real change/restoration results
are recorded separately below after the on-screen-confirmed test, if it can run safely.

## Build, tests and artifacts

Build from `launcher/` using the existing Java 17 / Android SDK 34 toolchain:

```powershell
.\gradlew.bat -p godaikin-poc --offline --no-daemon assembleDebug assembleDebugAndroidTest lintDebug
```

Install only `godaikin-poc/build/outputs/apk/debug/godaikin-read-only-poc-debug.apk`:

```text
adb install -r godaikin-poc/build/outputs/apk/debug/godaikin-read-only-poc-debug.apk
adb shell am start -n tv.widgetv.godaikinpoc/tv.widgetv.home.godaikin.TemperatureTestActivity
```

The original read-only credential-entry screen remains available as `.CloudTestActivity`.
Keep account entry private on the TV; the validation screen reuses the existing saved account.

Sanitized status/result only:

```text
adb shell run-as tv.widgetv.godaikinpoc cat files/temperature-validation-result.txt
```

Automated tests are synthetic: `CloudClientChecks`, `TemperatureChecks` and `CloudMockChecks`.
The Android runner uses isolated mock credentials, storage and Keystore aliases; it never contacts
the real cloud or submits real AC commands. It verifies exact single-use command payloads,
cross-device denial, power-command denial, missing approval, used/expired plans, network delay,
freshness/version gates, durable originals, compensation, rejection, ambiguous timeout,
offline/mismatched states, failed restore and explicit recovery.

Final automated results: 75 host cloud-client checks and 75 host temperature-validation checks
passed. Sony TV instrumentation passed **158 checks**: those same 150 synthetic protocol/transaction
checks plus eight real Android Keystore/persistence checks with isolated synthetic credentials.
These are not real AC command tests. Debug APK and instrumentation builds succeeded. Lint
completed with zero errors and 13 developer UI/configuration warnings (fixed TV orientation,
backup-rule advice, developer icon and hardcoded developer text).

Separate workspace APK: `GO-DAIKIN-Phase2.2-temperature-validation-debug.apk`.

The first user confirmation arrived after the initial 60-second plan expired. The test stopped
with `PLAN_EXPIRED` and sent no write. That expired approval was not reused. The revised workflow
uses a clearly displayed 15-minute review period and a bounded fresh-state wait after a NEW
on-screen confirmation. The two-minute device freshness gate remains unchanged.

## Source changes

New files under `launcher/godaikin-poc/`:

- `src/main/java/tv/widgetv/home/godaikin/TemperatureCommandGate.java`
- `src/main/java/tv/widgetv/home/godaikin/TemperatureValidation.java`
- `src/main/java/tv/widgetv/home/godaikin/TemperatureTestActivity.java`
- `checks/tv/widgetv/home/godaikin/TemperatureChecks.java`

Modified within that isolated module only: `GoDaikinCloudClient.java`, `CloudHttpsTransport.java`,
`CloudCredentialVault.java`, `CloudMockChecks.java`, manifest, build version (0.2, code 2), README and NOTICE.
This feasibility report is new. The stable launcher app and parent build settings remain untouched.

Rollback copies are preserved under workspace `backups/phase2.1-baseline/` for stable v2.0,
and `backups/phase2.2-baseline/` for the Phase 2.1 POC APK/source. No launcher rollback is needed
because the stable launcher was never overwritten. Removing only `tv.widgetv.godaikinpoc` removes
the developer experiment, not the launcher or its TP-Link configuration.

## Production stop condition

Production integration remains disabled. A real temperature command must be confirmed and the
original setting restored with fresh readback before it can be considered for a later phase.
Even after that test, no production UI/control integration is part of this change.

## Actual user-confirmed real command result

**PASSED: the revised test was explicitly confirmed on the TV; one adjustment and one restoration
were confirmed through fresh cloud-reported state.** No expired approval was reused.

The accepted transaction first waited using reads only until TV AC reported a fresh unchanged
original target. It then durably recorded 23 C, submitted the scoped 24 C command, confirmed it
with two consistent subsequent observations, submitted the 23 C compensation, and confirmed
restoration with two more consistent subsequent observations. The real test sent exactly two
temperature writes. It sent no power, mode, fan, swing or other-device writes.

| Stage | Target temperature | Cloud event / power | Reported timestamp (Malaysia interpretation) | State version |
| --- | --- | --- | --- | --- |
| Original recorded immediately before the write | 23 C | connected / ON | Fresh original required by the transaction | Recorded privately in encrypted journal |
| Changed and confirmed | 24 C | connected / ON | 2026-10-28 17:57:00 | 116684 |
| Original restored and confirmed | 23 C | connected / ON | 2026-10-28 17:57:16 | 116686 |

Power, mode and fan had to remain unchanged in both confirmation stages. HTTP success alone
was never used as command confirmation. The evidence demonstrates actual real-cloud command
submission and subsequent fresh reported-state transitions for this registered AC; no independent
observation of the physical unit's display or room-temperature response was performed.

Actual network timeout, invalid password, physical disconnection, process death during a write
and recovery on this real AC were not deliberately induced. Those failure paths are covered by
synthetic tests, not claimed as real hardware tests. Only target-temperature control has been
validated, and only for this TV AC/account/firmware combination. The remaining ACs were read-only.

The user-confirmed test completed with `Change confirmed: true` and `Original temperature restored
and confirmed: true`. Sanitized evidence is retained in workspace
`diagnostics/godaikin-phase22-real-control-result.txt`; credentials, device key and cloud ID are absent.

After completion the mock instrumentation package was removed and the TV returned to Octahedron.
Stable v2.0 source/configuration comparison still reported zero changed files, and its installed
APK SHA-256 still matched the preserved backup:
`5934594925f42719d30435ac89483f4bb307d4357b625088b345be06b1020b44`.

Final isolated debug APK: `GO-DAIKIN-Phase2.2-temperature-validation-debug.apk`, **148,017 bytes**,
SHA-256 `f0c1c5061e0943ef61559114db5bf5afa9e090394113223834dd9b4a055fb32d`.
Source archive: `Octahedron-Phase2.2-GODAIKIN-control-source.zip` (module, tests, reports and licenses;
no account data, build cache, raw cloud response or device identifiers).

The real-command/restoration prerequisite is now met for this narrow test. Production Daikin
controls and launcher UI integration remain disabled and require a later implementation stage.
