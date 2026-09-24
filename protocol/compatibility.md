# Zombie Protocol v1 checkpoint

`schemas/v1.json` contains versioned domain/response definitions. `examples/v1.json` has one fixture per definition. `make check` validates fixtures, required fields, additive evolution and samples captured from real gateway handlers.

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

IPTV channel items may include additive `favorite: true`. `GET
/v1/catalog?provider=iptv&favorites=1&offset=N` returns only currently available
favorited channels, with the same bounded paging. A paired device can `PUT` or
`DELETE /v1/iptv/favorites/{itemId}`; the gateway persists up to 256 stable
channel IDs in SQLite for the household. The playlist remains the source of
stream URLs and credentials; a removed channel is not resurrected by a favorite.
IPTV items may also include a bounded `category` from M3U group metadata. Catalog
responses include up to 128 sorted `categories`; `category=NAME` filters current
channels without changing their stable IDs. Category, favorites and search filters
can be combined and paged.

An empty event cursor establishes the current position. Invalid/expired/restarted cursors return 409 with a replacement cursor. Re-fetch current state and resume polling. `wait=0` is a nonblocking read. Unknown optional fields are accepted; unknown sections can be skipped or shown as a generic media row. Clients must not assume every provider or section is available.

Playback fields use `mimeType` and `resumePositionMs`. URLs are gateway-relative and use opaque short-lived session tickets; they never require provider credentials on the client. Tickets can be supplied to legacy MediaPlayer/external players that cannot reliably attach custom authorization headers. Tickets are bearer secrets and expire or become invalid when stopped.

IPTV items may include `subtitle` (current programme title) and `programmes` with title/start/end Unix seconds. These are semantic data, not guide coordinates. Artwork URLs point to the authenticated, bounded gateway derivative endpoint; no pixel layout, provider DTOs or remote HTML is sent.

Local planning uses ffprobe metadata and existing PASS/FAIL/UNKNOWN reports. Unknown support remains a candidate; no SDK/model inference upgrades it to PASS. Advanced overrides apply per playback request. Remux/transcode produce non-seekable fragmented MP4 with a six-hour job deadline. Dev.10 adds a timeline offset for gateway-side resume. Remote conversion and measured adaptive profiles remain pending. AudioTrack/SubtitleTrack remain unused drafts; the implemented inventory uses MediaTrack and TrackInventory. Tests using synthetic media/HTTP fixtures do not prove decoder or live provider compatibility.

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

`GET /v1/airplay/pairing` returns the AirPlay receiver's four-digit
PIN only to an authenticated paired device, with `Cache-Control: no-store`.
It is distinct from the six-digit operator code and unavailable when AirPlay is
disabled or its private worker cannot be reached. Clients must not persist it.

## Receiver and hardware additions (dev.7)

- `POST /v1/youtube/receiver`: claim one foreground receiver lease; returns
  `YouTubeReceiverState`, including a transient TV pairing code when ready.
- `GET /v1/youtube/receiver/{id}`: owner-only poll/lease refresh and optional
  semantic command. A play command includes an item ID accepted by normal playback.
- `POST /v1/youtube/receiver/{id}/state`: observed `ReceiverAcknowledgement`;
  an empty command ID is a heartbeat. Failed or stale commands cannot manufacture
  playback success. Only the owning paired device may acknowledge commands.
- `DELETE /v1/youtube/receiver/{id}`: release the worker and volatile pairing state.
- `PUT /v1/device/hardware`: persist a bounded `HardwareReport`. Scanner version
  and firmware/ABI fingerprint scope inventory. Changed fingerprints clear old
  capability probe results; unchanged fingerprints retain them. Inventory reports
  native DIAL and multicast as UNKNOWN; declared codecs are not measured support.

These are additive V1 endpoints. Clients without hardware inventory still register.
A hardware report is optional in registration and appears under the persisted
DeviceRecord registration. No MAC address, SSID or provider token is collected.

## Local tracks and subtitle additions (dev.10)

- `GET /v1/playback/{id}/tracks` returns `TrackInventory`. Stream indexes are
  session-scoped integers. Missing tools, remote and live sources return
  `available: false` with an empty list; missing evidence is not support.
- `GET /v1/playback/{id}/subtitles/{track}` returns `SubtitleCues` for selectable
  embedded text. FFmpeg simplifies ASS/SSA to SRT; the gateway normalizes SRT/VTT
  to plain text. Bitmap tracks are unselectable. Styling, sidecars and burn-in
  remain pending. Limits: 30 seconds extraction, 2 MiB intermediate text,
  5,000 cues, 4 KiB per cue and 1 MiB JSON response.
- `POST /v1/playback/{id}/audio` accepts `AudioSelection`, producing a new
  transcoded plan with the chosen stream. The client releases the old session
  before opening the replacement. Validation failures retain the original plan.
  A two-second capacity grace period allows the old FFmpeg process to be reaped.
- Optional `timelineOffsetMs` defaults to zero. Add it to decoder positions for
  progress, display and cues. Converted plans use `resumePositionMs: 0` and remain
  non-seekable; do not seek twice. Local transcode resume also uses this offset; remux starts at zero because exact keyframe resume is not implemented.
  Automatic language/native track selection remains pending. Older clients ignore
  the additive offset and cannot correctly display converted resume timelines;
  use the matching client for this feature.
- `GET /v1/playback/{id}/qualities` returns `QualityInventory` containing the
  current `selectedId` and selectable `QualityOption`s (including Auto and tiers
  bounded by actual video source resolution and device probe evidence).
- `POST /v1/playback/{id}/quality` accepts `QualitySelection` (`qualityId` and `positionMs`),
  producing a replacement plan at `positionMs`. The choice persists per device/video-kind
  for later eligible playbacks and falls back to Auto after errors or failed adaptation.

These endpoints require session ownership and device authentication; no paths,
provider headers or arbitrary process arguments are exposed. GatewayApi retains
its 1 MiB limit and permits 45 seconds for subtitle probe/extraction. The client
preserves selected subtitles across audio switches, with stale-result guards.

## dev.11: hierarchical browsing and remote adaptation

`GET /v1/browse` accepts a provider (`plex`, `jellyfin`, `stremio`), optional opaque
`parent`, `q` and `offset` (0–10000). `BrowsePage` contains a title, at most 40
`MediaItem`s and `nextOffset` (-1 ends). Follow the returned offset; it need not
advance by 40. Optional `browseId` means navigate; `playable: false` must not create
a playback session. Older item fields remain unchanged. Unknown kinds render a
text fallback. Parents/sources are scoped to the paired device and provider config
revision, expire after 30 minutes and can be evicted; 410 requires navigation from
an available ancestor/root. No provider URLs/tokens or presentation coordinates.

Private YouTube `audioUrl` remains a worker/core detail. Client playback receives
one gateway URL whether the origin is combined or adaptive. Shared playback POST
read timeout is 30 seconds to cover resolver and bounded remote probe work.
Converted streams retain the existing non-seekable/timeline-offset contract.

## dev.12: selected media receiver and Cast encoder budgets

`PUT /v1/media-receiver` with `{ "provider": "spotify" | "airplay" }` arms the
paired client for one shared output; a different active owner receives 409.
`GET /v1/media-receiver` renews the owner's 45-second foreground lease and returns
`MediaReceiver`: enabled/provider, nullable playback plan and optional semantic
NowPlaying. Other clients receive disabled state without the owner's plan.
`DELETE` releases only the caller's lease. Normal playback DELETE suppresses an
incoming source until idle or an explicit re-arm. GET is a polling/lease operation,
not a cached discovery endpoint; responses must not be cached.

Fresh source activity chooses a single live, non-seekable plan. Metadata changes
retain its session ID. Network failure is 502, not a fabricated idle state. The
selected Spotify receiver can send the existing finite player commands without
an operator code; other paired clients still require the operator code. Provider
credentials, worker addresses and raw metadata DTOs never cross this boundary.

CastGrant's existing `video` fields are actual encoder constraints. The current
sender validates bounds and fits both dimensions into them, aligning to 16 pixels.
Missing values use a conservative 640x360/24fps/800kbps candidate. A negotiated
budget is not runtime validation and does not imply arbitrary internal audio
capture, rotation recovery or native/OEM support.

## dev.13 playback position override

PlaybackRequest accepts optional `positionMs` (integer, 0–604800000). Omission
uses stored progress; explicit zero restarts VOD; live plans always start at the
current edge. Transcoded output retains its source timeline offset. Older gateways
may ignore this additive field, so exact retry-position behavior requires dev.13.
The change adds no provider URLs or credentials to the wire contract.

## dev.22 coordinated receiver replacement

`YouTubeReceiverRequest`, `MediaReceiverSelection` and `CastRequest` accept optional
`replaceExisting` (default false). Target-side media/YouTube selection may replace
that device's current transport after the new claim succeeds. It cannot steal a
lease from another device. Cast requires the target's separate
`Preferences.allowReceiverHandoff` consent, also false by default, when busy.
Readiness rechecks consent and prepares the new stream before retiring the old one.
Older gateways may ignore these additive fields and retain first-armed exclusion.

`PlaybackRequest.receiverId` optionally binds a YouTube command's plan to its lease;
a revoked lease returns `receiver_changed` even if upstream resolution finishes
later. Older clients retain the receiver-source fallback. `receiver.changed` is an
additive event containing a semantic transport name, never a worker token or URL.
Replacing a lease does not automatically restore or re-arm its upstream sender.

## dev.41 YouTube account data

The optional `GET /v1/youtube/account` status and `POST
/v1/youtube/account/authorization` / `POST
/v1/youtube/account/authorization/poll` expose only a short-lived user code,
verification URL, expiry and minimum poll interval. The Google device code,
OAuth client secret and access/refresh tokens remain on the Gateway. A paired
device may initiate/check authorization; `DELETE /v1/youtube/account` additionally
requires the current operator code and revokes the local grant.

`GET /v1/youtube/account/subscriptions` and `/playlists` return at most 40
semantic YouTube channel/playlist items and an optional opaque `nextPageToken`.
The URL accepts an optional `pageToken`; clients must bound their page history.
Returned `browseId` values are scoped to the paired device and existing YouTube
worker configuration. A disabled/unavailable worker can still show account lists
but cannot open those nodes. Account sign-in does not start TV Code reception or
change anonymous YouTube playback. Older gateways return 404 for these endpoints.
