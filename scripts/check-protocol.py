# /// script
# requires-python = ">=3.11"
# dependencies = ["jsonschema==4.26.0"]
# ///
"""Validate all draft models and fixtures, including permissive evolution."""

import json
import os
import subprocess
import tempfile
from pathlib import Path

from jsonschema import Draft202012Validator

root = Path(__file__).resolve().parents[1] / "protocol"
schema = json.loads((root / "schemas/v1.json").read_text())
Draft202012Validator.check_schema(schema)
fixtures = json.loads((root / "examples/v1.json").read_text())
assert set(fixtures) == set(schema["$defs"]), "Every draft definition needs a fixture"
for name, example in fixtures.items():
    selected = {
        "$schema": schema["$schema"],
        "$defs": schema["$defs"],
        "$ref": f"#/$defs/{name}",
    }
    validator = Draft202012Validator(selected)
    validator.validate(example)
    validator.validate({**example, "futureOptionalField": True})
    for field in schema["$defs"][name].get("required", []):
        incomplete = {key: value for key, value in example.items() if key != field}
        assert not validator.is_valid(incomplete), f"{name} accepts missing {field}"
print(
    f"PASS: {len(fixtures)} draft schemas + fixtures; required fields and additive evolution"
)

# Old senders omit the additive ceiling. New senders must request a bounded tier.
cast = Draft202012Validator({"$defs": schema["$defs"], "$ref": "#/$defs/CastRequest"})
cast.validate({"receiverId": "legacy-tv"})
for height in (720, 1080, 2160):
    cast.validate({"receiverId": "tv", "maxVideoHeight": height})
for height in (0, -1, 4320, "1080", True):
    assert not cast.is_valid({"receiverId": "tv", "maxVideoHeight": height})
print("PASS: additive bounded Cast ceiling and legacy omission")

# Audio is explicit opt-in and never silently becomes screen capture.
for mode in ("SCREEN", "AUDIO"):
    cast.validate({"receiverId": "tv", "mode": mode})
for mode in ("", "MEDIA", "audio", None, 1):
    assert not cast.is_valid({"receiverId": "tv", "mode": mode})
grant = Draft202012Validator({"$defs": schema["$defs"], "$ref": "#/$defs/CastGrant"})
legacy = fixtures["CastGrant"]
audio = {key: value for key, value in legacy.items() if key != "video"}
audio.update(
    mode="AUDIO",
    audio={"codec": "aac", "sampleRate": 44100, "channels": 2, "bitrate": 128000},
)
grant.validate(audio)
assert not grant.is_valid({**audio, "video": legacy["video"]})
assert not grant.is_valid(
    {key: value for key, value in audio.items() if key != "audio"}
)
assert not grant.is_valid(
    {key: value for key, value in legacy.items() if key != "video"}
)
assert not grant.is_valid({**audio, "audio": {**audio["audio"], "sampleRate": 48000}})
print("PASS: explicit audio-only grant and legacy screen contract")

# Optional integration check against the explicitly selected gateway checkout.

core = os.environ.get("ZOMBIE_CORE_DIR")
if core:
    with tempfile.TemporaryDirectory() as tmp:
        capture = Path(tmp) / "responses.json"
        subprocess.run(
            [
                "go",
                "test",
                "-p",
                "2",
                "./internal/server",
                "-run",
                "^TestWireContracts$",
                "-count=1",
            ],
            cwd=Path(core) / "gateway",
            env={
                **os.environ,
                "GOMAXPROCS": "2",
                "ZOMBIE_CONTRACT_CAPTURE": str(capture),
            },
            check=True,
        )
        for name, sample in json.loads(capture.read_text()).items():
            Draft202012Validator(
                {"$defs": schema["$defs"], "$ref": f"#/$defs/{name}"}
            ).validate(sample)
    print("PASS: live handler response contracts")
else:
    print("INFO: set ZOMBIE_CORE_DIR for live gateway contract validation")

receipt = Draft202012Validator(
    {"$defs": schema["$defs"], "$ref": "#/$defs/CompanionMediaReceipt"}
)
for invalid in ("../file", "", "A" * 32):
    assert not receipt.is_valid({"mediaId": invalid, "state": "UPLOADED"})
status = Draft202012Validator(
    {"$defs": schema["$defs"], "$ref": "#/$defs/CompanionMediaStatus"}
)
assert not status.is_valid({"state": "ACCEPTED"})
status.validate({"state": "ACCEPTED", "mediaId": "a" * 32, "title": "My file"})
print("PASS: bounded companion media receipts and state")

# Declaration-only native inventory remains additive to legacy scanner reports.
hardware = Draft202012Validator(
    {"$defs": schema["$defs"], "$ref": "#/$defs/HardwareReport"}
)
old_hardware = fixtures["HardwareReport"]
hardware.validate(old_hardware)
encoder = {
    "name": "declared",
    "types": ["video/avc"],
    "acceleration": "UNKNOWN",
    "profiles": [{"mime": "video/avc", "profile": 1, "level": 256}],
}
hardware.validate({**old_hardware, "encoders": [encoder]})
assert not hardware.is_valid(
    {**old_hardware, "encoders": [{**encoder, "probeCandidates": ["h264-2160-high"]}]}
)
assert not hardware.is_valid(
    {**old_hardware, "encoders": [{**encoder, "acceleration": "PASS"}]}
)
print("PASS: native inventory remains additive and cannot claim encoder probe success")
