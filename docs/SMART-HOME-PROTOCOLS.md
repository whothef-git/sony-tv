# Direct LAN protocol implementation — version 2.0

This is a new Java/JCA implementation of community-documented TP-Link wire behavior, not an official TP-Link SDK. No Python runtime, external server, Home Assistant, cloud login, or downloaded reference implementation is bundled. Protocol compatibility still requires testing against each device's actual hardware/firmware.

## Primary research references

Researched on 2026-10-28 using the maintained python-kasa project's documentation and implementation:

- [Supported devices and discovery](https://python-kasa.readthedocs.io/en/latest/)
- [Discovery source](https://github.com/python-kasa/python-kasa/blob/master/kasa/discover.py)
- [KLAP transport](https://github.com/python-kasa/python-kasa/blob/master/kasa/transports/klaptransport.py)
- [Legacy AES transport](https://github.com/python-kasa/python-kasa/blob/master/kasa/transports/aestransport.py)
- [SMART request envelope](https://github.com/python-kasa/python-kasa/blob/master/kasa/protocols/smartprotocol.py)
- [Energy units](https://github.com/python-kasa/python-kasa/blob/master/kasa/smart/modules/energy.py)
- [Legacy Kasa strip children](https://github.com/python-kasa/python-kasa/blob/master/kasa/iot/iotstrip.py)

The Python package is not an Android/JVM dependency; interoperability primitives were implemented independently in Java. Research copies remain outside the distributable source tree under local diagnostics. Do not describe this port as manufacturer-supported or as verified on the user's hardware without device tests.

## Implemented transport paths

| Device/firmware path | Implementation |
|---|---|
| P110 KLAP | Local HTTP handshake, credential challenge validation, KLAP v1/v2 negotiation, signed AES-CBC request/response frames and per-session sequence numbers |
| P110 legacy AES | RSA-1024 handshake required by that legacy protocol, encrypted local login, AES-CBC passthrough, login version 1 or 2 selected from discovery/manual configuration |
| KP303 legacy IOT | TCP 9999, length-prefixed XOR-obfuscated JSON, actual `children[].id` values in per-outlet request context |
| KP303 IOT over KLAP | Same IOT command envelope over an advertised KLAP transport, when discovery identifies an IOT family |
| Discovery | Legacy XOR UDP plus TDP public-key queries, RSA-2048/OAEP and AES-CBC encrypted discovery payloads |
| Other TPAP, HTTPS-only or SMART child APIs | Not implemented; setup/firmware error rather than fake data or demo fallback |

P110 uses `get_device_info`, `set_device_info` and `get_energy_usage`. Supported energy fields are converted from milliwatts to watts and watt-hours to kWh; missing/unsupported measurements remain unknown. KP303 monitoring is not requested or shown.

AUTO tries a KLAP handshake first and uses a legacy AES handshake only if a KLAP endpoint is absent/incompatible. A valid KLAP challenge with mismatched credentials fails authentication, without trying default passwords. Discovery advertises the preferred cipher/login version where available. Manual protocol selection is provided for discovery failures. Newer firmware can require enabling third-party compatibility in the native TP-Link app; TPAP is not implemented here.

Sessions and tokens are memory-only, are replaced on failure/network changes, and are renewed after a bounded lifetime. Commands are never automatically resent after ambiguous timeout. The next poll reads actual state. Success requires a response and a fresh state read matching the requested power/outlet state.

## Discovery and lifecycle

Bounded UDP queries on 9999, 20002 and 20004 run on the Android active network, with interface broadcast addresses and unicast probes of saved IPs. Discovery includes the standard legacy query and the newer public-key/CRC-framed query with encrypted-response support. Wi-Fi and Ethernet use the same ConnectivityManager/Network APIs. Broadcast replies are unicast; no multicast group or multicast lock is used. There is no full-subnet TCP sweep.

Polling is scheduled approximately every 10 seconds and rediscovery every 30 seconds while real Smart Home is foreground/active. A device has at most one network operation in flight. Three bounded network workers prevent main-thread blocking. Hidden/paused views stop periodic tasks; no foreground service or listening control server is introduced.

Known limitations: AP/client isolation, VLAN boundaries, router broadcast filtering and unsupported future protocols can require manual IP/protocol setup. Local device addresses are IPv4 in this implementation. Discovery failure does not prove absence of a device.

## Security boundaries

- Credentials are encrypted using an Android Keystore AES-256-GCM key, with profile identity as authenticated data. Ordinary device metadata never contains account passwords, tokens, private keys or session material.
- Credential entry uses a FLAG_SECURE window and excludes autofill. Do not collect it through chat, screenshots, logs or shell arguments.
- No cloud authentication, default-password guessing, external backend, root, or unauthenticated control server.
- Local HTTP is limited by the device client to numeric private/link-local IPv4 addresses and redirects are disabled. Existing weather/JAKIM services remain HTTPS; system certificate and hostname verification is unchanged.
- Android's network-security XML permits cleartext because dynamically configured IP/CIDR rules cannot be expressed in domain-config. The device client enforces the local-address restriction. This policy does not provide TLS for local devices.
- Legacy Kasa XOR provides obfuscation, **not encryption or authentication**. Use only on a trusted LAN. Legacy AES/RSA and vendor local protocols have their own security limitations; this app cannot upgrade device firmware security.
- Cached states are last-confirmed information and are not loaded as ONLINE after restart. Power OFF is independent of connectivity.
- Stable MAC/device identity is checked before control, and outlet identifier sets must match before targeted commands are sent.

## GO DAIKIN investigation

The manufacturer's [GO DAIKIN leaflet](https://www.daikin.com.my/daikinCatalogue/catalog/Leaflet_%20GODAIKIN-0422-G-L.pdf) identifies the AWM61A01 network adapter and describes in-home/out-of-home app control. This does not establish a supported local API for the user's controllers. Other Daikin BRP/Onecta/thermostat APIs cannot be assumed compatible merely because the mobile app is GO DAIKIN.

Five named Daikin entries are retained in Manage Devices as disabled setup requirements. No Daikin endpoint, cloud login or real control is implemented. Required next evidence: indoor-unit and Wi-Fi/controller models, firmware, and a verified compatible local or authorized public method. A mobile-account login alone is insufficient.
