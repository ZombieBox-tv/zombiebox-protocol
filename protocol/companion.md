# Consented phone pairing and remote commands (API v1)

Device authentication and companion authentication are separate credential scopes.
Both use `X-Zombie-Device` plus `Authorization: Bearer ...`, but companion grants
cannot call ordinary device/provider/admin APIs. All identifiers and secrets use
lowercase hexadecimal. Do not log request bodies, QR payloads or tokens.

A registered TV requests `POST /v1/device/companions/invitations` with its reachable
`gateway` base URL. It receives a 384px PNG (base64), six-digit fallback code and
remaining lifetime, at most two minutes. The QR embeds `CompanionQR`: version,
locator, invitation ID and ephemeral secret; no reusable device/admin credential.

The phone sends `POST /v1/companion/join` with `name` and either `invitationId` +
`secret` or `code`. Joining consumes the invitation atomically and returns a
pending request plus a new token, persisted only as SHA-256 by the gateway.
The TV polls `GET /v1/device/companions`, compares the displayed six-digit code,
and locally accepts/rejects via `POST /v1/device/companions/{request}/decision`
with `accept`. Only the invitation's target can decide. Dismissal rejects.
The phone polls `POST /v1/companion/requests/{request}` with `token` in the body.
Expired, denied and replayed invitations grant no access. Approval creates a
durable, target-scoped grant; request expiry does not revoke an approved grant.

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
