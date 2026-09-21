# Additive dev.19 contracts

`PlaybackPlan.subtitleId` and `TrackInventory.subtitleId` optionally identify the
text track selected by gateway language policy. Absence means no automatically
selected subtitle. Manual client choice remains authoritative for the owned session.

`GET /v1/diagnostics` returns `DiagnosticReport` for an authenticated paired device.
The report uses an explicit server allowlist and no-store caching. It contains no
credentials, URLs, installation IDs, media titles or session tickets. Clients should
continue accepting future optional fields. Report version is separate from APK,
capability suite and protocol versions; protocol remains V1.

Forty-four schemas/fixtures and live gateway handlers cover additive evolution.
Older APKs ignore the new optional fields; Cast transport is unchanged.
