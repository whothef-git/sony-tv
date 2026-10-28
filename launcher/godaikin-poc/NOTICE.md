# GO DAIKIN protocol adaptation

The Java protocol in `GoDaikinCloudClient.java` adapts `auth.py`, `api.py` and
`types.py` from [doubleukay/godaikin-ha](https://github.com/doubleukay/godaikin-ha),
commit `255666c00e748f425c2a87ec2bb1e319c922e6df` (integration manifest 2.1.5).

Copyright 2025 Woon Wai Keen. Licensed under Apache License 2.0; the complete license
is included as `LICENSE-GODAIKIN`. Modifications are identified in the Java source header.
No upstream NOTICE file was present in the inspected repository root.

Phase 2.2 extends the same adaptation with stable-ID-scoped temperature serialization, targeted
state reads, fresh-state validation and explicit confirmation/restoration. `climate.py` was also
reviewed for temperature bounds and calling behavior. Production launcher controls remain disabled.

This module is an unofficial feasibility experiment, not a Daikin product or supported API.
Phase 2.3 adds exact-payload fan/mode/separately approved power experiments for the previously
pinned TV AC only, retaining the same auth/client protocol attribution and security boundaries.
The source license does not establish permission to use Daikin's cloud service.
Other newly written project files retain the parent repository's MIT license.
