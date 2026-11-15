import httpx
import pytest

from ai_service.container import Services
from ai_service.rag.chunking import Page, chunk_pages
from tests.conftest import auth, pdf_bytes, upload
from tests.fakes import CountingEmbedder, FakeDrive, FakeWeb


async def _collections(client: httpx.AsyncClient) -> list[dict[str, object]]:
    r = await client.get("/v1/collections", headers=auth("ai:ingest"))
    assert r.status_code == 200
    return list(r.json())


async def test_pdf_ingest_lists_the_collection(client: httpx.AsyncClient, services: Services) -> None:
    accepted = await upload(
        client, services, "product", "guide.pdf", pdf_bytes("Page one about lanterns.", "Page two about batteries.")
    )
    job = (await client.get(f"/v1/jobs/{accepted['job_id']}", headers=auth("ai:ingest"))).json()
    assert job["status"] == "DONE" and job["progress"] == 100
    [col] = await _collections(client)
    assert col["code"] == "product" and col["documents"] == 1 and col["chunks"] >= 1 and col["status"] == "READY"
    assert col["last_sync"]
    docs = (await client.get("/v1/collections/product/documents", headers=auth("ai:ingest"))).json()
    assert docs[0]["status"] == "READY" and docs[0]["source_type"] == "PDF"


async def test_unchanged_is_skipped_changed_is_replaced_and_delete_removes_chunks(
    client: httpx.AsyncClient, services: Services
) -> None:
    emb = services.embedder
    assert isinstance(emb, CountingEmbedder)
    first = await upload(client, services, "product", "guide.pdf", pdf_bytes("Original text about lanterns."))
    embedded = emb.texts_embedded
    assert embedded > 0
    await upload(client, services, "product", "guide.pdf", pdf_bytes("Original text about lanterns."))
    assert emb.texts_embedded == embedded  # unchanged content hash: nothing re-embedded
    again = await upload(client, services, "product", "guide.pdf", pdf_bytes("Completely new text about kettles."))
    assert again["document_id"] == first["document_id"]
    assert emb.texts_embedded > embedded
    assert await services.store.count("product") == 1  # old chunks replaced, not added
    hits = await services.store.query((await emb.embed(["lanterns"]))[0], ["product"], 5)
    assert all("lanterns" not in h.text for h in hits) and any("kettles" in h.text for h in hits)
    r = await client.delete(f"/v1/collections/product/documents/{first['document_id']}", headers=auth("ai:ingest"))
    assert r.status_code == 204
    assert await services.store.count("product") == 0
    assert (
        await client.delete(f"/v1/collections/product/documents/{first['document_id']}", headers=auth("ai:ingest"))
    ).status_code == 404
    assert await _collections(client) == []


async def test_upload_validation(client: httpx.AsyncClient) -> None:
    h = auth("ai:ingest")
    r = await client.post("/v1/collections/product/documents", headers=h, files={"file": ("a.txt", b"hello", "text/plain")})
    assert r.status_code == 415
    r = await client.post(
        "/v1/collections/product/documents", headers=h, files={"file": ("a.pdf", b"not a pdf at all", "application/pdf")}
    )
    assert r.status_code == 415 and r.json()["type"].endswith("/not-a-pdf")
    r = await client.post(
        "/v1/collections/product/documents",
        headers=h,
        files={"file": ("big.pdf", b"%PDF-" + b"0" * (1024 * 1024 + 10), "application/pdf")},
    )
    assert r.status_code == 413
    r = await client.post(
        "/v1/collections/Bad Code/documents", headers=h, files={"file": ("a.pdf", b"%PDF-1", "application/pdf")}
    )
    assert r.status_code == 422


async def test_a_broken_pdf_fails_that_document_only(client: httpx.AsyncClient, services: Services) -> None:
    accepted = await upload(client, services, "product", "broken.pdf", b"%PDF-1.4 this is not really a pdf")
    job = (await client.get(f"/v1/jobs/{accepted['job_id']}", headers=auth("ai:ingest"))).json()
    assert job["status"] == "FAILED" and job["failures"], job
    [col] = await _collections(client)
    assert col["status"] == "FAILED"


async def test_drive_sync_is_incremental_and_a_bad_file_does_not_fail_the_job(
    client: httpx.AsyncClient, services: Services
) -> None:
    drive = services.drive
    assert isinstance(drive, FakeDrive)
    drive.add("f1", "Brand voice", "2026-11-01T10:00:00Z", "text", b"Baracel speaks plainly and warmly.")
    drive.add("f2", "Logo rules.pdf", "2026-11-01T10:00:00Z", "pdf", pdf_bytes("Keep clear space around the logo."))
    drive.add("f3", "Broken", "2026-11-01T10:00:00Z", "pdf", b"garbage")
    r = await client.post("/v1/collections/brand/sources/drive", headers=auth("ai:ingest"), json={"folder_id": "folder123"})
    assert r.status_code == 202
    await services.worker.wait_idle()
    job = (await client.get(f"/v1/jobs/{r.json()['job_id']}", headers=auth("ai:ingest"))).json()
    assert job["status"] == "PARTIAL" and [f["source_ref"] for f in job["failures"]] == ["f3"]
    docs = {d["source_ref"]: d for d in (await client.get("/v1/collections/brand/documents", headers=auth("ai:ingest"))).json()}
    assert docs["f1"]["status"] == "READY" and docs["f2"]["status"] == "READY" and docs["f3"]["status"] == "FAILED"
    # second sync: only the file that changed (and the failed one) is downloaded again
    drive.downloads.clear()
    drive.add("f1", "Brand voice", "2026-11-05T10:00:00Z", "text", b"Baracel speaks plainly, warmly and briefly.")
    await client.post("/v1/collections/brand/sources/drive", headers=auth("ai:ingest"), json={"folder_id": "folder123"})
    await services.worker.wait_idle()
    assert sorted(drive.downloads) == ["f1", "f3"]


async def test_drive_not_configured_is_a_clear_failure(client: httpx.AsyncClient, services: Services) -> None:
    from ai_service.rag.drive import UnconfiguredDrive

    services.worker._drive = UnconfiguredDrive()
    r = await client.post("/v1/collections/brand/sources/drive", headers=auth("ai:ingest"), json={"folder_id": "folder123"})
    await services.worker.wait_idle()
    job = (await client.get(f"/v1/jobs/{r.json()['job_id']}", headers=auth("ai:ingest"))).json()
    assert job["status"] == "FAILED"


PAGE = "<html><head><title>{t}</title></head><body><article><h1>{t}</h1><p>{body}</p><a href='/about'>About</a><a href='https://other.example/x'>x</a></article></body></html>"


async def test_web_ingest_same_domain_robots_and_page_limit(client: httpx.AsyncClient, services: Services) -> None:
    web = services.web
    assert isinstance(web, FakeWeb)
    long = "Baracel makes lanterns that last a decade and ship in recycled boxes. " * 8
    web.pages = {
        "https://baracel.example/": PAGE.format(t="Home", body=long),
        "https://baracel.example/about": PAGE.format(t="About", body=long + " Founded in 2019."),
    }
    web.blocked = {"https://baracel.example/private"}
    h = auth("ai:ingest")
    r = await client.post(
        "/v1/collections/brand/sources/web", headers=h, json={"urls": ["https://baracel.example/", "https://other.example/"]}
    )
    assert r.status_code == 422 and r.json()["type"].endswith("/different-domain")
    r = await client.post("/v1/collections/brand/sources/web", headers=h, json={"urls": ["ftp://baracel.example/"]})
    assert r.status_code == 422
    r = await client.post(
        "/v1/collections/brand/sources/web",
        headers=h,
        json={"urls": ["https://baracel.example/", "https://baracel.example/private"], "follow_links": True},
    )
    await services.worker.wait_idle()
    job = (await client.get(f"/v1/jobs/{r.json()['job_id']}", headers=h)).json()
    assert (
        job["status"] == "PARTIAL"
        and job["failures"][0]["source_ref"].endswith("/private")
        and "robots" in job["failures"][0]["error"]
    )
    assert web.fetched == [
        "https://baracel.example/",
        "https://baracel.example/about",
    ]  # /private never fetched, other domain never followed
    refs = {
        d["source_ref"] for d in (await client.get("/v1/collections/brand/documents", headers=h)).json() if d["status"] == "READY"
    }
    assert refs == {"https://baracel.example/", "https://baracel.example/about"}


async def test_reindex_reembeds_everything(client: httpx.AsyncClient, services: Services) -> None:
    emb = services.embedder
    assert isinstance(emb, CountingEmbedder)
    await upload(client, services, "product", "guide.pdf", pdf_bytes("Text about lanterns."))
    before = emb.texts_embedded
    r = await client.post("/v1/collections/product/reindex", headers=auth("ai:ingest"))
    assert r.status_code == 202
    await services.worker.wait_idle()
    assert emb.texts_embedded > before and await services.store.count("product") >= 1
    assert (await client.post("/v1/collections/nothing/reindex", headers=auth("ai:ingest"))).status_code == 404


def test_chunking_overlap_page_numbers_and_size() -> None:
    para = "Sentence number {n} talks about lanterns and batteries and cables. "  # about 17 tokens each
    pages = [
        Page(1, "\n\n".join(para.format(n=i) * 3 for i in range(30))),
        Page(2, "\n\n".join(para.format(n=100 + i) * 3 for i in range(30))),
    ]
    chunks = chunk_pages(pages, max_tokens=500, overlap_tokens=80)
    assert len(chunks) > 3
    assert all(len(c.text) <= 2100 for c in chunks)
    assert chunks[0].page == 1 and chunks[-1].page == 2
    assert [c.index for c in chunks] == list(range(len(chunks)))
    first_last_para = chunks[0].text.split("\n\n")[-1]
    assert first_last_para in chunks[1].text  # overlap on a paragraph boundary


@pytest.mark.parametrize("text", ["", "\n\n  \n"])
def test_chunking_empty(text: str) -> None:
    assert chunk_pages([Page(1, text)]) == []
