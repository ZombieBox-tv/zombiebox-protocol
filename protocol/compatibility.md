# Zombie Protocol v1 checkpoint

`schemas/v1.json` contains 18 versioned domain/response definitions. `examples/v1.json` has one fixture per definition. `make check` validates fixtures, required fields, additive evolution and samples captured from real gateway handlers.

## Transport and identity

HTTP/1.1 + JSON. API, UI schema, playback and capability versions are independent and currently 1. Registration requires `installationId` and `pairingCode`; protocol mismatch returns 426. These required fields replace the pre-implementation registration draft.

`POST /v1/devices/register` issues a device bearer token. Subsequent requests use `X-Zombie-Device` and `Authorization: Bearer …`. Re-pairing rotates the token. `GET /health` remains unauthenticated. Provider edits additionally require `X-Zombie-Admin-Code`; GET provider status never returns URLs, tokens or local paths.

| Endpoint | Behavior |
|---|---|
| `GET /v1/device` | Registered device, preferences, capability report; no pairing code/token |
| `PUT /v1/device/preferences` | Persist mode, EN/ES UI and media-language preferences |
| `PUT /v1/device/capabilities` | Persist explicitly measured PASS/FAIL/UNKNOWN reports |
| `GET /v1/modules` | Actual disabled/starting/healthy/degraded state |
| `GET /v1/home?provider=&q=` | Semantic hero and bounded sections |
| `GET /v1/catalog?provider=&q=&offset=` | Page of up to 40 semantic items, total and nextOffset (-1 when complete) |
| `GET /v1/providers` | Configuration flags and server-management status |
| `PUT /v1/providers/{id}` | Partial, write-only configuration; omitted retains, empty clears |
| `GET /v1/events?cursor=` | Long poll up to 20 seconds; bounded ring, device filtering |
| `POST /v1/playback` | `{itemId}` → session and direct-play candidate |
| `PUT /v1/playback/{id}/progress` | Trusted session item + position, duration and state |
| `DELETE /v1/playback/{id}` | Cancel session and active relay requests |
| `GET /v1/streams/{id}[/{resource}]?ticket=` | Session-scoped media relay; range/HLS support |

An empty event cursor establishes the current position. Invalid/expired/restarted cursors return 409 with a replacement cursor. Re-fetch current state and resume polling. `wait=0` is a nonblocking read. Unknown optional fields are accepted; unknown sections can be skipped or shown as a generic media row. Clients must not assume every provider or section is available.

Playback fields use `mimeType` and `resumePositionMs`. URLs are gateway-relative and use opaque short-lived session tickets; they never require provider credentials on the client. Tickets can be supplied to legacy MediaPlayer/external players that cannot reliably attach custom authorization headers. Tickets are bearer secrets and expire or become invalid when stopped.

IPTV items may include `subtitle` (current programme title) and `programmes` with title/start/end Unix seconds. These are semantic data, not guide coordinates. No artwork pipeline, pixel layout, provider DTOs or remote HTML is sent.

Current playback plans are DIRECT_PLAY candidates; codec-probe-based REMUX/TRANSCODE/EXTERNAL planning remains unimplemented. Subtitle/audio-track definitions remain draft. Tests using synthetic media/HTTP fixtures do not prove decoder or live provider compatibility.
