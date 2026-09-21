# Additive dev.21 contracts

Protocol version remains 1. PlaybackRequest optionally accepts networkAdaptation
(default true for Auto). Existing forced modes and quality identifiers are unchanged.

- GET /v1/network/sample: paired, uncompressed fixed 1-MiB stream; single-use
  X-Zombie-Sample header. No provider URL is accepted.
- POST /v1/device/network: NetworkSampleReport → NetworkEstimate. Receipt bytes
  and elapsed time are bounded and scoped to the authenticated device.
- GET /v1/search?q=: SearchResults with six SearchSection values and existing
  semantic MediaItem values. READY/DISABLED/UNAVAILABLE distinguish missing results
  from unavailable providers. Existing per-provider browse remains version 1.

GatewayApi adds an optional streamed sample operation. It uses an 8-KiB read buffer,
refuses redirects, detects pairing changes and is disconnected with other active
requests on close. Cast consumes the shared transport update without invoking it.
