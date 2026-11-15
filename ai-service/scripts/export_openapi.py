"""Writes docs/ai-service-openapi.json. Run from ai-service/:  uv run python scripts/export_openapi.py"""

import json
from pathlib import Path

from ai_service.core.config import Settings
from ai_service.main import create_app

app = create_app(Settings(_env_file=None))  # type: ignore[call-arg]  # builds the schema only, no lifespan, no connections
out = Path(__file__).resolve().parents[2] / "docs" / "ai-service-openapi.json"
out.write_text(json.dumps(app.openapi(), indent=2, ensure_ascii=False) + "\n")
print(f"wrote {out}")
