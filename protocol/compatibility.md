# Zombie Protocol v1 draft

`schemas/v1.json` contains 11 proposed domain models plus the implemented Health response. `examples/v1.json` has one fixture per definition. This is a bootstrap draft, not a claim that registration, Home, playback or events are implemented.

- HTTP/1.1 + JSON baseline. Long polling for events; WebSocket remains optional.
- Keep API, UI schema, playback and capabilities versions independent.
- Accept unknown optional fields. Unknown UI section types must be skipped or rendered as a fallback, never crash.
- Gateway sends semantic content, not pixel positions, font sizes or upstream provider DTOs.
- Fixtures contain fake IDs/local URLs; they are not playable media or live provider data.
- Breaking field changes require a compatibility decision. Add fixtures before adding runtime endpoints.
- `make check` validates definitions, fixtures, required fields and additive evolution using locked jsonschema dependencies.

Pending contracts: pairing/token issuance, registration response, event batch/poll timeout/cursor expiry, protocol negotiation and detailed media planning. Keep these explicitly unimplemented rather than returning success stubs.
