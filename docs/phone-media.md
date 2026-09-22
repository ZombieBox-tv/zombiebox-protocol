# Companion file transport

Dev.30 adds `mediaAvailable` to CompanionStatus and the CompanionMediaReceipt,
CompanionMediaPlay and CompanionMediaStatus schemas. Existing SCREEN/AUDIO relay
grants are unchanged; MEDIA is not a valid MediaProjection/RTSP grant mode.

A companion authenticates `PUT /v1/companion/media/{32-hex-id}` with a raw fixed
Content-Length body (1..268435456 bytes), then posts optional title JSON to
`/v1/companion/media/{id}/play`. UPLOADED and ACCEPTED receipts are distinct from
verified receiver playback. GET `/v1/companion/media` returns NONE or the owner's
accepted mediaId/title; DELETE of an owned ID cancels it. Approved target identity
comes from the grant, not phone input. No stream ticket is returned to the phone.

GatewayApi/CompanionTransport supply bounded streaming upload with identity proof,
no redirects, finite deadlines, progress and close/disconnect cancellation. The
minSdk9 shared library adds no native payload or capture API. Cast alone exposes
file selection. Existing Client receiver plans now honor VOD live/seekable/mode
semantics; legacy plans without the optional values retain live behavior.
