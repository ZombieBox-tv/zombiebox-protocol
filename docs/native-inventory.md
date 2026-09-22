# Additive native inventory contract

HardwareReport retains its existing required fields. Optional encoders (32),
displays (8), inventoryLimited and per-codec acceleration/profiles are additive.
Acceleration is UNKNOWN, HARDWARE or SOFTWARE: a declaration, never a test result.
Profile tuples retain Android numeric profile/level values and their MIME type.
Encoder probeCandidates must be empty. Display refresh uses integer milli-Hertz;
activeModeId zero denotes logical metrics without an active native mode declaration.
Each display allows up to 16 modes. Server validation additionally checks IDs,
profile/MIME membership and active-mode dimensions/refresh consistency.

Existing hardware fixture and endpoint remain compatible. Decoder/display/capture
performance, OEM operation and physical output still require separate evidence.
