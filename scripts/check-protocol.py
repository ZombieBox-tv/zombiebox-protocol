# /// script
# requires-python = ">=3.11"
# dependencies = ["jsonschema==4.26.0"]
# ///
"""Validate all draft models and fixtures, including permissive evolution."""

import json
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

# Validate actual gateway response shapes against the same schemas.
import os
import subprocess
import tempfile

with tempfile.TemporaryDirectory() as tmp:
    capture = Path(tmp) / "responses.json"
    environment = {
        **os.environ,
        "GOMAXPROCS": "2",
        "ZOMBIE_CONTRACT_CAPTURE": str(capture),
    }
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
        cwd=root.parent / "gateway",
        env=environment,
        check=True,
    )
    for name, sample in json.loads(capture.read_text()).items():
        Draft202012Validator(
            {"$defs": schema["$defs"], "$ref": f"#/$defs/{name}"}
        ).validate(sample)
print("PASS: live handler response contracts")
