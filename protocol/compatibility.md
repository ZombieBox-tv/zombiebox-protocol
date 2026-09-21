# Zombie Protocol v1 checkpoint

`schemas/v1.json` contains 23 versioned domain/response definitions. `examples/v1.json` has one fixture per definition. `make check` validates fixtures, required fields, additive evolution and samples captured from real gateway handlers.

## Transport and identity

HTTP/1.1 + JSON. API, UI schema, playback and capability versions are independent and currently 1. Registration requires `installationId` and `pairingCode`; protocol mismatch returns 426. These required fields replace the pre-implementation registration draft.

`POST /v1/devices/register` issues a device bearer token. Subsequent requests use `X-Zombie-Device` and `Authorization: Bearer …`. Re-pairing rotates the token. `GET /health` remains unauthenticated. Provider edits additionally require `X-Zombie-Admin-Code`; GET provider status never returns URLs, tokens or local paths.

| Endpoint | Behavior |
|---|---|
| `GET /v1/device` | Registered device, preferences, capability report; no pairing code/token |
| `GET/PUT /v1/device/preferences` | Read/persist mode, EN/ES UI, media-language preferences and receiver opt-in |
| `PUT /v1/device/capabilities` | Persist explicitly measured PASS/FAIL/UNKNOWN reports |
| `GET /v1/modules` | Actual disabled/starting/healthy/degraded state |
| `GET /v1/home?provider=&q=` | Semantic hero and bounded sections |
| `GET /v1/catalog?provider=&q=&offset=` | Page of up to 40 semantic items, total and nextOffset (-1 when complete) |
| `GET /v1/providers` | Configuration flags and server-management status |
| `PUT /v1/providers/{id}` | Partial, write-only configuration; omitted retains, empty clears |
| `GET /v1/events?cursor=` | Long poll up to 20 seconds; bounded ring, device filtering |
| `POST /v1/playback` | `{itemId, mode?}` → direct/remux/transcode/external local plan; remote streams remain direct candidates |
| `PUT /v1/playback/{id}/progress` | Trusted session item + position, duration and state |
| `DELETE /v1/playback/{id}` | Cancel session and active relay requests |
| `GET /v1/streams/{id}[/{resource}]?ticket=` | Session-scoped media relay; range/HLS support |

An empty event cursor establishes the current position. Invalid/expired/restarted cursors return 409 with a replacement cursor. Re-fetch current state and resume polling. `wait=0` is a nonblocking read. Unknown optional fields are accepted; unknown sections can be skipped or shown as a generic media row. Clients must not assume every provider or section is available.

Playback fields use `mimeType` and `resumePositionMs`. URLs are gateway-relative and use opaque short-lived session tickets; they never require provider credentials on the client. Tickets can be supplied to legacy MediaPlayer/external players that cannot reliably attach custom authorization headers. Tickets are bearer secrets and expire or become invalid when stopped.

IPTV items may include `subtitle` (current programme title) and `programmes` with title/start/end Unix seconds. These are semantic data, not guide coordinates. No artwork pipeline, pixel layout, provider DTOs or remote HTML is sent.

Local planning uses ffprobe metadata and existing PASS/FAIL/UNKNOWN reports. Unknown support remains a candidate; no SDK/model inference upgrades it to PASS. Advanced overrides apply per playback request. Remux/transcode currently produce non-seekable fragmented MP4 with zero resume offset and a six-hour job deadline. Remote conversion and measured adaptive profiles remain pending. Subtitle/audio-track definitions remain draft. Tests using synthetic media/HTTP fixtures do not prove decoder or live provider compatibility.

## Mirroring additions (dev.4)

`allowCasting` defaults to false. See [mirroring endpoints and lifecycle](../docs/development/mirroring.md). `CastRequest`, `CastGrant`, `CastReceivers` and `ActiveCast` describe the authenticated negotiation. `LIVE_LOW_LATENCY` is an additional PlaybackPlan mode using HLS MPEG-TS; receivers must inspect `live`/`seekable`, not infer a measured latency guarantee. Live playback progress is not added to Continue Watching.

Existing clients that PUT preferences without `allowCasting` disable receiving. Cast sources and internal HLS credentials are ephemeral and are not catalog entries. Gateway restarts clear live casts and invalidate their tickets; persisted device/provider configuration survives.

## Services and browser additions (dev.5)

- `GET /v1/integrations`: bounded independent checks with implementation and
  Full/Edge support descriptors. READY means the adapter/process endpoint
  responded; it does not certify accounts, playback or physical compatibility.
- `GET /v1/player/spotify`: semantic Now Playing. `POST` accepts a finite
  `PlayerCommand`; writes require the current operator code and a paired device.
- `GET /v1/player/spotify/authorization`: transient device-auth code/URL,
  administrator-only. Provider access/refresh tokens never leave the gateway.
- `POST /v1/browser`: URL → one owned 960×540 ephemeral BrowserSession.
  `GET /v1/browser/{id}/frame` returns JPEG capped at 1 MiB.
  `POST /v1/browser/{id}/input` accepts navigation/key/text commands;
  `DELETE /v1/browser/{id}` releases the private browser/profile. Idle expiry is
  90 seconds. No remote JavaScript evaluation or provider HTML/DTO API is exposed.

AirPlay catalog now has separate video and audio live sources; inactive sources
are not playable. Spotify uses a live MP3 bridge. These additions do not change
protocol/UI/playback version 1 and old clients may ignore new optional features.
