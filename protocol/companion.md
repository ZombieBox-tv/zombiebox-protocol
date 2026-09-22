# Consented phone pairing and remote commands (API v1)

Device authentication and companion authentication are separate credential scopes.
Both use `X-Zombie-Device` plus `Authorization: Bearer ...`, but companion grants
cannot call ordinary device/provider/admin APIs. All identifiers and secrets use
lowercase hexadecimal. Do not log request bodies, QR payloads or tokens.

A registered TV explicitly displays a QR by requesting
`POST /v1/device/companions/invitations` with its reachable `gateway` base URL.
The returned 384px PNG encodes QR version 2, gateway locator, invitation ID and a
256-bit single-use secret. The server expires it **five minutes after creation**;
reopening/re-scanning does not extend that expiry. `remainingMs` is bounded by
300000. Redemption atomically consumes the invitation and creates an APPROVED,
target-scoped companion grant. There is no second TV acceptance dialog: displaying
and sharing this short-lived capability is the local consent. The QR contains no
administrator or reusable TV credential. Previously stored version-1 invitations
retain their old pending-consent behavior until expiry; no migration upgrades old
secrets to automatic approval. Only the hash of the new durable phone token is stored.

Network/URL onboarding starts with unauthenticated `GET /v1/companion/targets`.
It exposes up to 32 currently polling TV IDs/names, not credentials or diagnostic
reports. Selection posts `name`, `targetId` and a persistent random 64-hex
`clientKey` to `/v1/companion/join`. A target ID is **not** authorization. The phone
and TV show the same generated six-digit comparison; only a local TV decision
`POST /v1/device/companions/{request}/decision` with `accept` creates a grant.
Pending requests expire in two minutes. The old code-based entry remains supported
for older callers, with local consent; new Cast UI does not ask users to pre-create
a code. The phone polls `/v1/companion/requests/{request}` with its temporary token.

A rejection may add `ignore24h: true` (default false). The gateway persists expiring,
target-scoped hashes of the installation key and source IP for 24 hours, across
restarts. It coalesces concurrent requests from either identity and caps pending
requests per target. An attacker changing both key and IP cannot be recognized as
the same physical device; rate/capacity bounds still apply. IP blocking can affect
phones sharing a proxy/NAT. The private hashes are never returned in inventory or
status. Possession of a newly displayed QR may authorize despite a network-request
block, since it conveys new local consent. Existing approved grants are unaffected.

Before disclosing a saved token to any address, the phone requests
`POST /v1/companion/proof` with its grant `id` and a fresh 32-hex-character `nonce`.
The expected `proof` is lowercase hex HMAC-SHA256 over UTF-8
`zombie-companion-v1\n{id}\n{nonce}`, keyed by ASCII lowercase hex SHA-256 of the
UTF-8 token. Compare in constant time. This checks possession at a discovered
locator; it does not encrypt trusted-LAN HTTP or defeat an active network relay.

`GET /v1/companion/status` returns scoped target identity, foreground remote
availability, Cast readiness and the last command result. `POST
/v1/companion/commands` accepts only the finite semantic action/provider list.
Five commands/second/grant and sixteen queued commands/target are maximums.
Commands expire after two seconds and are consumed at most once through the
TV's `POST /v1/device/remote/poll` (`active` boolean). The returned `remainingMs`
must be reduced by client round-trip/dispatch time. No reconnect replay occurs.
The TV sends `POST /v1/device/remote/ack` with command `id` and `status`:
EXECUTED, BUSY or UNSUPPORTED. Delivery alone never proves execution. Unacknowledged
receipts expire. Only active Client UI receives input; consent/system dialogs do
not accept remote commands. This is not ADB, arbitrary key injection or CEC.

`POST /v1/companion/cast` always selects the grant's TV, ignoring external target
IDs. PUT/DELETE `/v1/companion/cast/{cast}` and POST `.../{cast}/ready` enforce
sender ownership. TV incoming-Cast preferences and projection consent still apply.
DELETE `/v1/device/companions/{grant}` or `/v1/companion/session` revokes access,
drops pending commands and terminates that companion's Cast session. Approval and
revocation are persisted in SQLite; transient commands/heartbeats are not.

Version remains 1. Unknown optional fields are tolerated. Named response schemas
and fixtures supplement host HTTP/race and cross-language proof-vector tests.
Physical camera scanning, TV input timing and mirroring remain separate gates.

## Remote text entry

TV poll requests may advertise `inputId`, an ephemeral 32-hex focus lease for an
eligible owned text field. Companion status exposes `textInputId` only while the
TV is active. `TEXT` commands carry `inputId` and 1–512 Unicode characters (no
control characters). The gateway rejects stale leases; Client checks the lease
again at delivery and pastes at the current selection, respecting input filters.
Commands expire after two seconds, are consumed once and are never persisted or
echoed in result receipts. Passwords, settings/consent/system dialogs and other apps
are excluded. The feature does not install an IME or inject OS-wide text.


## Direct URL queues (dev.35)

`GET/POST/DELETE /v1/companion/media/queue` use the same scoped companion proof as
file transfer. POST supplies a random 32-hex `id` and 1–16 `{url,title}` items;
retrying the same owned ID is idempotent. Status contains only ID, phase,
zero-based index, count and title, never URLs or credentials. One queue is active
at a time, matching the global Cast slot. It is ephemeral and expires in six hours.

Each public HTTP(S) direct file is downloaded to bounded temporary storage before
probing/planning. Downloads have a two-minute deadline and a 256-MiB limit, including
unknown-length responses. No provider credentials are forwarded. Redirects and
resolved addresses cannot target private/special networks; playlists, HTML and
credential-bearing URL authorities are rejected. Only owned natural completion
advances the queue. Stop, revoke, replacement and failure cancel it. Gateway restart
clears it. Closing the phone queue screen leaves it running; explicit Stop ends it.
The paired target can explicitly cancel preparation/playback with
`DELETE /v1/cast/queue`; stale per-session cleanup cannot cancel another queued item.
This API does not promise webpage extraction, resumable byte transfers or arbitrary
HLS/DASH URL import. Provider adapters retain their existing manifest support.

`POST /v1/playback/{session}/adapt` accepts `positionMs` and returns `{plan:null}`
or a replacement PlaybackPlan. It requires an owned, eligible Auto session and two
fresh distinct bandwidth observations agreeing on a lower ceiling. A response does
not revoke the prior session: the client adopts the replacement and then deletes
the prior stream. This is a controlled restart, not seamless ABR.

`MediaReceiverSelection.provider=universal` explicitly enables cross-receiver
listening and requires target casting/handoff consent. Spotify/AirPlay and the
YouTube lease can remain armed while only one transport plays. Private worker
`epoch` values fence pre-handoff responses/acknowledgements; they are not pairing
credentials. Unsupported older workers fall back to closing their lease. Client
YouTube command polling still follows its foreground lifecycle.
