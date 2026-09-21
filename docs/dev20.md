# Additive recovery and automatic receiver fields

`PlaybackRequest.quality` optionally accepts `STANDARD` or `LOW`; LOW is allowed
only with TRANSCODE. Older requests preserve standard behavior. Unsupported profiles
are rejected before media processing; arbitrary FFmpeg flags never cross the wire.

`MediaReceiverSelection.provider` and `MediaReceiver.provider` also accept `auto`.
The snapshot retains the configured selector; the actual active transport is carried
by the existing plan item and NowPlaying provider. NowPlaying explicitly accepts
`spotify`, `airplay` and the automatic idle selector. This corrects its previous
Spotify-only schema restriction for the shared receiver field.

Protocol remains V1; older receivers using manual Spotify/AirPlay are unchanged.
Cast transport and shared Android HTTP classes are unchanged.
