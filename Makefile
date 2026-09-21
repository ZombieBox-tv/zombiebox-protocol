
.PHONY: format format-check
format:
	python3 scripts/format.py
format-check:
	python3 scripts/format.py --check

.PHONY: check build
check:
	uv run --locked --script scripts/check-protocol.py
build:
	bash scripts/android.sh :shared:assembleDebug :shared:lintDebug
