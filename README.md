# zombiebox-protocol

Versioned JSON contracts and the Android HTTP transport shared by both APKs.

This is an independent repository in the Zombie Box workspace. Remotes and hosted
releases are not configured yet; local commits/tags and dependency pins are real.

- `protocol`: provider-neutral schemas and fixtures.
- `android-shared`: minSdk9 HttpURLConnection transport and stable credentials model.
- Standalone Gradle build includes `:shared`; no dependency on either APK.

```sh
make check
make build
# Also validate live Go response contracts with an explicit core checkout:
ZOMBIE_CORE_DIR=../gateway-core make check
```

Use additive evolution and tolerate unknown optional fields. Provider DTOs and
credentials do not belong in the client-facing contract. Protocol, capability,
playback/UI versions and application release versions remain separate.
Shared transport uses no AndroidX/coroutines dependency or native libraries.

## Development rules

Run `make format` and `make format-check`. Formatters are pinned and downloaded
on first use. See [AGENTS.md](AGENTS.md), [history provenance](docs/history.md),
[component work](docs/PLANNING.md) and [local milestone registry](docs/milestones.json).
The central workspace owns product-wide ADRs, the original specification, the UI
reference, M0–M11 exit gates and the complete development/validation gap audit.
Physical devices over USB/ADB are the default; automated checks do not establish
legacy runtime or end-to-end account/media compatibility.

Dev.13: Adds optional bounded PlaybackRequest.positionMs; older clients retain stored-history behavior.

Dev.16: Additive suite/cache/operation probe fields and bounded browser pointer coordinates. Legacy fixtures remain accepted.

## License

First-party code: [GPL-3.0-only](LICENSE). See [NOTICE](NOTICE) for third-party scope.

Dev.19: Additive subtitle selection IDs and DiagnosticReport contract; 44 schema fixtures and live response validation.

Dev.20: Additive LOW/STANDARD playback quality and automatic media-receiver selection; NowPlaying accepts AirPlay and automatic idle states.
