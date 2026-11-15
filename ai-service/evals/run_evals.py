"""Retrieval evals against a RUNNING service: hit rate at 3 and at 8, and p95 latency.

Not part of the normal test run. It uploads the sample PDFs (evals/samples.py) to the four collections, waits for
indexing, asks every golden question with ALL collections allowed, and checks whether the expected source title is in the
top 3 and top 8 sources.

  uv run python evals/run_evals.py --url http://localhost:8000 --token "$(../scripts/get-ai-token.sh)"
"""

import argparse
import io
import statistics
import sys
import time
from pathlib import Path

import httpx
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

sys.path.insert(0, str(Path(__file__).parent))
from samples import GOLDEN, SAMPLES  # noqa: E402


def make_pdf(pages: list[str]) -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    for text in pages:
        y = 800
        for line in text.split("\n"):
            c.drawString(50, y, line[:105])
            y -= 16
        c.showPage()
    c.save()
    return buf.getvalue()


def wait_for(client: httpx.Client, job_id: str) -> None:
    for _ in range(300):
        job = client.get(f"/v1/jobs/{job_id}").json()
        if job["status"] in ("DONE", "PARTIAL", "FAILED"):
            if job["status"] != "DONE":
                raise SystemExit(f"job {job_id} ended as {job['status']}: {job}")
            return
        time.sleep(1)
    raise SystemExit("timeout waiting for indexing")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8000")
    ap.add_argument("--token", required=True)
    args = ap.parse_args()
    ingest = httpx.Client(base_url=args.url, headers={"Authorization": f"Bearer {args.token}"}, timeout=300)

    for code, docs in SAMPLES.items():
        for name, pages in docs.items():
            r = ingest.post(f"/v1/collections/{code}/documents", files={"file": (name, make_pdf(pages), "application/pdf")})
            r.raise_for_status()
            wait_for(ingest, r.json()["job_id"])
    print(f"indexed {sum(len(d) for d in SAMPLES.values())} documents in {len(SAMPLES)} collections")

    allowed = list(SAMPLES)
    hit3 = hit8 = 0
    latencies: list[float] = []
    for question, collection, title in GOLDEN:
        body = {
            "message": question, "mode": "answer", "allowed_collections": allowed, "history": [],
            "user": {"id": "eval", "name": "Eval", "roles": []},
        }
        started = time.perf_counter()
        r = ingest.post("/v1/chat", json=body)
        latencies.append(time.perf_counter() - started)
        r.raise_for_status()
        sources = r.json()["sources"]
        rank = next((i for i, s in enumerate(sources) if s["title"] == title and s["collection"] == collection), None)
        hit3 += rank is not None and rank < 3
        hit8 += rank is not None and rank < 8
        print(f"  {'ok ' if rank is not None and rank < 3 else ('low' if rank is not None else 'MISS')} rank={rank}  {question}")

    n = len(GOLDEN)
    p95 = sorted(latencies)[max(0, int(round(0.95 * n)) - 1)]
    print(f"\nquestions: {n}\nhit rate @3: {hit3 / n:.0%}\nhit rate @8: {hit8 / n:.0%}")
    print(f"latency (answer incl. LLM): median {statistics.median(latencies):.2f}s, p95 {p95:.2f}s")


if __name__ == "__main__":
    main()
