# zombiebox-protocol: component work

The product milestones relevant to this repository are M0, M1, M2, M3, M4, M5, M6, M7, M8, M9, M11.
The local registry is a component projection of the workspace plan. Closing a
component task does not close a product-wide milestone or a physical validation gate.

Current increment: independent repository/build/dependency boundaries with filtered
history. Remaining feature development follows the ordered workspace audit:
tracks/subtitles and lifecycle; provider navigation/virtualization; measured
capabilities/native-first health; remote media adaptation; receiver finishing;
Edge operations and reproducible releases. Implement only this component's part,
and evolve shared protocol contracts in their owning repository.

Keep a separate validation track for hardware/account/latency/memory evidence.
Use development checkpoint tags until complete exit gates are evidenced. Hosted
issues/milestones can be attached to the shared GitHub Project once remotes exist.

## dev.11 increment

Additive BrowsePage/browseId/playable contract and playback POST timeout for remote probes; no provider URLs or credentials in the client contract.
No product milestone or physical/account gate is completed by this checkpoint.

## dev.12 increment

MediaReceiver snapshot/selection contracts and documented CastGrant constraints. Physical validation remains separate.

## dev.13 increment

Adds optional bounded PlaybackRequest.positionMs; older clients retain stored-history behavior.
No product milestone or physical/account gate closes with this checkpoint.

## dev.16 increment

Additive suite/cache/operation probe fields and bounded browser pointer coordinates. Legacy fixtures remain accepted.
Product exit gates and physical/account acceptance remain open.

## dev.17 increment

Adds optional guide freshness and integration-hint fields; first-party GPL licensing. Shared Android transport behavior is unchanged.

No product milestone or physical gate is closed.

## dev.21 increment

Additive measured-network and federated-search contracts; bounded streaming transport sample. API version remains 1.
No physical, account or product milestone closes.

## dev.22 increment

Additive receiver replacement requests, receiver-bound playback and target-owned Cast handoff consent; 49 schema fixtures.
No product or physical acceptance gate closes.

Verification: 49 draft schemas/fixtures and live handler response contracts pass; fields are additive and default replacement consent remains false.

## dev.23 increment

Shared bounded IPv4 scanner and untrusted semantic locators; no token transfer or API-version change.
Product exit gates and deferred physical acceptance remain open.


## dev.24 increment

Additive companion schemas/fixtures, shared consent/remote wire mapping and cross-language gateway proof. Protocol version remains 1; no native library or Cast-only dependency enters the shared module.
Full visual/capture policy, extended Remote, HEVC/4K and other product gates remain open; physical acceptance stays deferred.

## dev.25 increment

Additive bounded decoder probeCandidates, probe prerequisites and testedAt evidence fields. API1 and legacy fixtures remain compatible.

## dev.27 increment

Adds optional CastRequest.maxVideoHeight (720/1080); omission preserves the old sender contract. No Android transport or thin-Client code changed. Schema checks cover old requests and unsupported ceilings.

## dev.29 increment

Additive SCREEN/AUDIO request and grant mode; legacy grants retain video, audio-only grants require the bounded AAC contract and omit video. Shared Android transport code is unchanged.
Product milestones and physical acceptance remain open.

## dev.30 increment

Additive companion media receipts/status and shared bounded streaming upload transport; existing SCREEN/AUDIO grants unchanged.
Product milestones and deferred physical gates remain open.

## dev.31 increment

Additive HardwareReport codec profiles, encoder roles, declared acceleration and display modes; legacy reports remain valid. Encoder declarations cannot carry decoder probe candidates.
Product milestone and physical/public distribution gates remain open.

## dev.34 implementation checkpoint

Additive target selection, QR v2 consent, 24-hour rejection policy and bounded TEXT commands with ephemeral input leases. HTTP protocol remains v1; legacy code consent remains supported.
Product exit gates and deferred physical acceptance remain open.
