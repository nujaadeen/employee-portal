#!/usr/bin/env python3
"""Writes the demo knowledge base PDFs (the evals samples: product, brand, campaign) into a directory as <collection>__<name>.pdf."""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "ai-service", "evals"))
sys.path.insert(0, os.path.dirname(__file__))
from minipdf import make_pdf  # noqa: E402
from samples import SAMPLES  # noqa: E402

out = sys.argv[1]
for collection in ("product", "brand", "campaign"):   # what Sales may search
    for name, pages in SAMPLES[collection].items():
        with open(os.path.join(out, f"{collection}__{name}"), "wb") as f:
            f.write(make_pdf([p.split("\n") for p in pages]))
