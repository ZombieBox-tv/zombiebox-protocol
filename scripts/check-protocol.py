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
    selected = {"$schema": schema["$schema"], "$defs": schema["$defs"], "$ref": f"#/$defs/{name}"}
    validator = Draft202012Validator(selected)
    validator.validate(example)
    validator.validate({**example, "futureOptionalField": True})
    for field in schema["$defs"][name].get("required", []):
        incomplete = {key: value for key, value in example.items() if key != field}
        assert not validator.is_valid(incomplete), f"{name} accepts missing {field}"
print(f"PASS: {len(fixtures)} draft schemas + fixtures; required fields and additive evolution")
