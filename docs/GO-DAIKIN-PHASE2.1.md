# Phase 2.1 — GO DAIKIN cloud feasibility

Date: 2026-10-28. Stable launcher: Octahedron 2.0, version code 11.
User-provided unit: Daikin FTKH35BV1MF, GO DAIKIN firmware V1.2.5, TV AC IP 192.168.50.93.

## Finding

**Android-only cloud authentication, registered-device discovery and reported status reads work
on the user's Sony TV with their GO DAIKIN account.** All five registered ACs were returned.
No AC controls were implemented or sent. Hardware response to control remains unverified.

The local IP is not needed for this cloud method. The earlier local probes found IP reachability
but refused HTTP/HTTPS connections and no compatible local discovery response.

The proof of concept is a separate debug test package within the existing repository, not a new
launcher. Its pure Java client is suitable for later integration after a separately authorized
control-validation stage. The installed launcher, Smart Home UI and KP303/Tapo code are unchanged.

## Reference, licensing and dependencies

Inspected [doubleukay/godaikin-ha](https://github.com/doubleukay/godaikin-ha), commit
[`255666c00e748f425c2a87ec2bb1e319c922e6df`](https://github.com/doubleukay/godaikin-ha/tree/255666c00e748f425c2a87ec2bb1e319c922e6df),
manifest version 2.1.5. Reviewed auth, API, data types, coordinator, configuration and climate code.

The repository explicitly describes an **unofficial, unaffiliated** integration. Its source uses
Apache-2.0 (copyright 2025 Woon Wai Keen). The Java adaptation identifies modifications and retains
attribution and the complete license in `launcher/godaikin-poc/`. No upstream root NOTICE was found.
The source license is not an API agreement or evidence of Daikin authorization or API stability.

Python imports include `boto3`/botocore for Cognito, `aiohttp` for HTTPS, asyncio/dataclasses and
Home Assistant/voluptuous for host integration and configuration. Although manifest `requirements`
and `dependencies` are empty, the source is not a standalone dependency-free Python client.

The Android port uses platform HTTPS, JSON, executors, monotonic time and Android Keystore.
No Python runtime, Home Assistant or additional Android runtime library is required.

## Authentication and token handling

Source: [`auth.py`](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/auth.py).

- AWS Cognito region `ap-southeast-1`.
- Public app client ID `36f6piu770fotfscvhi3jb1vb7`; not an account secret.
- HTTPS Cognito `InitiateAuth`, `USER_PASSWORD_AUTH`, with username and password.
- Returned ID token is placed directly in the API's `authorization` header, matching the reference.
- Refresh uses `REFRESH_TOKEN_AUTH`; no account password is retained by the cloud client.
- Session access is serialized. Expiry uses a monotonic deadline with an early-refresh buffer.
- On rejected refresh/API authorization, the client clears its session. No retry login loop.
- MFA, password-reset/custom challenges, federated login and other authentication flows are not
  implemented; the test stops and reports the requirement. No MFA or account checks are bypassed.

The wire request is implementable without AWS access keys or an AWS SDK. AWS documents direct
REST and client-side password/refresh flows:
[InitiateAuth](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_InitiateAuth.html),
[Cognito API overview](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/Welcome.html).

Actual password login and API authorization succeeded for this account. Expiring-token refresh,
invalid-password and MFA behavior were tested with mocks only, not deliberately induced on the real account.

## Discovery and read-only status

Source: [`api.py`](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/api.py),
[`types.py`](https://github.com/doubleukay/godaikin-ha/blob/255666c00e748f425c2a87ec2bb1e319c922e6df/custom_components/godaikin/types.py).

The reference posts `requestData: {type: 1, value: <account email>}` to
`https://c7zkf7l933.execute-api.ap-southeast-1.amazonaws.com/prod/gethomepageinfowithsubscription`.
Returned `data` entries include stable `ThingName`, display name, IP, subscription fields and
`shadowState` containing settings, temperatures, firmware, event and update timestamp.
Cloud identifiers are deduplicated; missing states remain UNKNOWN rather than defaulting to OFF.

Important: upstream `get_shadow_state()` calls `_set_desired_state()` before reading from
`publishdevicestate`. Even its empty desired-state request is excluded here. This proof of concept
never calls `publishdevicestate` at all; status comes only from the registered-device response.
The HTTPS transport enforces the allowed destinations and payload shapes before opening a socket.

## Actual Sony TV results

User entered credentials privately on the TV and activated the read-only test. Communication
originated directly from the Android TV application. Sanitized result was retrieved by ADB;
no password, account email, token, cloud device key or raw cloud body was captured.

| Registered AC | Cloud power snapshot | Cloud event | Firmware | Cloud update timestamp |
| --- | --- | --- | --- | --- |
| Master Bedroom | OFF | connected | V1.2.5 | 2026-10-28 16:29:21 |
| Bilik Study | OFF | disconnected | V1.2.5 | 2026-10-22 00:40:17 |
| Dining | OFF | disconnected | V1.2.3 | 2026-10-13 15:00:59 |
| Bilik Ibu | OFF | disconnected | V1.2.3 | 2026-10-08 12:52:49 |
| TV | ON | connected | V1.2.5 | 2026-10-28 17:16:46 |

These are returned cloud snapshots, not independently verified live hardware states. The API
timestamp timezone is unverified. The three disconnected ACs have stale snapshots; their listed
OFF values must not be interpreted as their current physical power state. Even a connected event
alone cannot establish freshness. No AC was switched on/off or had settings changed by this test.

## Controls present in the reference, not implemented here

Upstream sends `publishdevicestate` type 3 with a desired-state payload and device shadow key.
It implements power; target temperature (16–31 C); Cool/Dry/Fan modes; Auto/Low/Medium/High fan;
vertical/horizontal swing; presets (Eco/Breeze/Powerful/Sleep); and status LED where supported.
Mappings in the reference include mode Cool=1/Fan=2/Dry=4 and fan Auto=128/Low=2/Medium=4/High=8.
Auto HVAC mode is not established by this integration. Capability flags and actual unit support
must be checked before future controls. None of these controls is claimed to work on this hardware.

The reference's mold-proof feature is simulated using control commands; it is deliberately absent.
Its accumulated energy sensor resets on HA restart, and its graph-energy method is described in
source as untimely/unused. Those values are not evidence of trustworthy live energy monitoring.

## Limitations and security

- Unofficial private service endpoints, app-client configuration and response schema may change.
- No supported public GO DAIKIN developer API, published rate limit or third-party service
  authorization was established by this investigation. Cloud access remains dependent on Daikin.
- Internet and account/subscription/service availability are required. The API name includes
  subscription handling; exact entitlement enforcement was not established.
- Regional account compatibility is not universal. The upstream
  [login issue](https://github.com/doubleukay/godaikin-ha/issues/7) reports regional login failures.
  This Malaysian account worked with the specified reference configuration; other pools are unverified.
- No continuous polling was added. Upstream's seven-second polling is not an established safe API quota.
- Platform TLS certificate and hostname checks remain enabled. No redirects, cleartext cloud
  requests, certificate interception, root, control server or external backend.
- Separate app-private AES-256-GCM/Android Keystore vault; backup disabled; no hardcoded account
  credentials; no TP-Link credential access; no secrets in intents/logs/results/source archives.
- Tokens are RAM-only, never written to preferences. Raw device keys are not retained in the model.
- Credential entry disables screenshots/autofill/view-state saving. Java immutable strings cannot
  be guaranteed zeroized; debug APK/ADB access remains trusted-development access, not production security.
- Network operations run on a worker with bounded connect/read timeouts; no automatic retries.
  Repeated test activation is disabled while pending. Client transport has no write endpoint.

## Verification and build

- Host JVM: **75 synthetic mock checks passed**, no real network.
- Sony TV instrumentation: **79 checks passed** (same 75 mock checks plus four real Android
  Keystore/persistence checks), using isolated synthetic credentials and no real cloud requests.
- Real TV cloud test: login accepted; five ACs discovered; five reported-status snapshots read.
- Actual AC power, temperature, mode, fan and swing commands: **not implemented, not tested**.
- Debug proof-of-concept and instrumentation APK builds succeeded. Production release variant disabled.
- Android debug lint completed with zero errors and 11 warnings: fixed TV orientation, Android 12+
  data-extraction-rule advice (backup is disabled), missing developer-package icon and eight hardcoded
  developer UI text warnings. These do not constitute production readiness.
- All baseline launcher `app/src` files, app build configuration and parent Gradle settings have
  matching before/after SHA-256 hashes. No launcher/TP-Link source changed.
- Installed launcher SHA-256 still equals preserved v2.0 APK:
  `5934594925f42719d30435ac89483f4bb307d4357b625088b345be06b1020b44`.
- Test instrumentation package removed after completion; TV returned to the existing launcher.

POC APK: `launcher/godaikin-poc/build/outputs/apk/debug/godaikin-read-only-poc-debug.apk`
(38,637 bytes). The package is `tv.widgetv.godaikinpoc`, not `tv.widgetv.home`.

Workspace rollback copies: `backups/phase2.1-baseline/Octahedron-2.0-smart-home.apk` and
`backups/phase2.1-baseline/WidgeTV-2.0-source.zip`. No rollback install is needed because the
stable launcher was not replaced. Removing the POC package leaves v2.0 intact.

## Files and next stage

All implementation files are new under `launcher/godaikin-poc/`: build/settings, manifest,
Java cloud client, HTTPS transport, Keystore vault, private test activity, host mock checks,
Android instrumentation checks, module README, .gitignore and upstream license/notice.
This report is also new. No existing source file was modified.

A future separately approved stage can integrate the validated read path with the existing
repository and Smart Home UI, retaining disabled controls until capability checks and an explicitly
authorized reversible hardware-control test succeed. It must distinguish cached cloud status,
reachability and power; preserve ordering/focus; and use conservative lifecycle-aware polling.
No production release or main-UI integration was performed in this phase.
