"""Background worker: one job at a time per collection, progress reported in the ingest_job table."""

import asyncio
import logging
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from ai_service.core.config import Settings
from ai_service.core.errors import ProblemError
from ai_service.db.models import Document, IngestJob
from ai_service.rag.drive import DriveClient, DriveFile
from ai_service.rag.extract import ExtractionError, extract_html, extract_pdf, extract_plain
from ai_service.rag.ingest import Indexer
from ai_service.rag.web import WebFetcher, same_domain_links, validate_urls

log = logging.getLogger(__name__)


def _safe_error(exc: Exception) -> str:
    """A short reason that is safe to store and return: our own messages, otherwise only the exception type."""
    if isinstance(exc, ExtractionError):
        return str(exc)[:500]
    if isinstance(exc, ProblemError):
        return exc.detail[:500]
    return type(exc).__name__


def upload_path(settings: Settings, collection: str, document_id: str) -> Path:
    return Path(settings.upload_dir) / collection / f"{document_id}.pdf"


class JobWorker:
    def __init__(
        self,
        settings: Settings,
        maker: async_sessionmaker[AsyncSession],
        indexer: Indexer,
        drive: DriveClient,
        web: WebFetcher,
    ) -> None:
        self._s, self._maker, self._indexer, self._drive, self._web = settings, maker, indexer, drive, web
        self._queue: asyncio.Queue[str] = asyncio.Queue()
        self._locks: dict[str, asyncio.Lock] = {}
        self._tasks: set[asyncio.Task[None]] = set()
        self._consumer: asyncio.Task[None] | None = None

    async def start(self) -> None:
        # Jobs left over from a previous run go back in the queue.
        async with self._maker() as s:
            for job in (await s.execute(select(IngestJob).where(IngestJob.status.in_(["QUEUED", "RUNNING"])))).scalars():
                job.status = "QUEUED"
                self._queue.put_nowait(job.id)
            await s.commit()
        self._consumer = asyncio.create_task(self._loop())

    async def stop(self) -> None:
        if self._consumer:
            self._consumer.cancel()
        for t in list(self._tasks):
            t.cancel()

    def enqueue(self, job_id: str) -> None:
        self._queue.put_nowait(job_id)

    async def wait_idle(self) -> None:
        """For tests: wait until every queued job has finished."""
        while not self._queue.empty() or self._tasks:  # noqa: ASYNC110
            await asyncio.sleep(0.01)

    async def _loop(self) -> None:
        while True:
            job_id = await self._queue.get()
            task = asyncio.create_task(self._run(job_id))
            self._tasks.add(task)
            task.add_done_callback(self._tasks.discard)

    async def _run(self, job_id: str) -> None:
        async with self._maker() as s:
            job = await s.get(IngestJob, job_id)
            if job is None:
                return
            lock = self._locks.setdefault(job.collection, asyncio.Lock())
            async with lock:
                job.status, job.progress = "RUNNING", 0
                await s.commit()
                status, error = "DONE", None
                failures: list[dict[str, str]] = []
                try:
                    failures, total = await self._dispatch(s, job)
                    if failures:
                        status = "FAILED" if len(failures) >= total else "PARTIAL"
                except ExtractionError as exc:
                    status, error = "FAILED", str(exc)
                except Exception as exc:
                    log.exception("job %s failed", job_id)
                    status, error = "FAILED", _safe_error(exc)
                await s.rollback()
                job = await s.get(IngestJob, job_id, populate_existing=True) or job  # a rollback in a runner expired it
                job.status, job.error, job.failures = (
                    status,
                    error or (failures[0]["error"] if failures and status == "FAILED" else None),
                    failures,
                )
                job.progress = 100
                job.finished_at = datetime.now(UTC)
                await s.commit()
                log.info("job %s finished: kind=%s status=%s failures=%d", job_id, job.kind, job.status, len(job.failures))

    async def _dispatch(self, s: AsyncSession, job: IngestJob) -> tuple[list[dict[str, str]], int]:
        match job.kind:
            case "PDF":
                return await self._pdf(s, job)
            case "DRIVE":
                return await self._drive_sync(s, job)
            case "WEB":
                return await self._web_sync(s, job)
            case "REINDEX":
                return await self._reindex(s, job)
        raise ValueError(job.kind)

    async def _progress(self, s: AsyncSession, job: IngestJob, done: int, total: int) -> None:
        job.progress = min(99, int(100 * done / max(total, 1)))
        await s.commit()

    async def _fail(self, s: AsyncSession, doc: Document, exc: Exception) -> dict[str, str]:
        doc_id, source_ref = doc.id, doc.source_ref  # read before the rollback expires the objects
        await s.rollback()
        error = _safe_error(exc)
        fresh = await s.get(Document, doc_id)
        if fresh is not None:
            fresh.status, fresh.error = "FAILED", error
            await s.commit()
        return {"source_ref": source_ref, "error": error}

    async def _pdf(self, s: AsyncSession, job: IngestJob) -> tuple[list[dict[str, str]], int]:
        payload = dict(job.payload)
        doc = await s.get(Document, payload["document_id"])
        if doc is None:
            raise ExtractionError("The document was deleted before it was indexed")
        try:
            title, pages = extract_pdf(upload_path(self._s, doc.collection, doc.id).read_bytes())
            await self._indexer.index(s, doc, pages, title=doc.title or title, force=bool(payload.get("force")))
        except Exception as exc:
            failure = await self._fail(s, doc, exc)
            return [failure], 1
        return [], 1

    async def _drive_sync(self, s: AsyncSession, job: IngestJob) -> tuple[list[dict[str, str]], int]:
        payload, collection = dict(job.payload), job.collection
        files = await self._drive.list_files(payload["folder_id"], bool(payload.get("recursive", True)))
        failures: list[dict[str, str]] = []
        for i, f in enumerate(files):
            doc = await self._indexer.get_or_create(s, collection, "DRIVE", f.id, f.name)
            if not payload.get("force") and doc.status == "READY" and doc.remote_modified == f.modified_time:
                await self._progress(s, job, i + 1, len(files))
                continue  # incremental: unchanged since the last sync
            failures += await self._index_drive_file(s, doc, f, bool(payload.get("force")))
            await self._progress(s, job, i + 1, len(files))
        return failures, len(files)

    async def _index_drive_file(self, s: AsyncSession, doc: Document, f: DriveFile, force: bool) -> list[dict[str, str]]:
        try:
            kind, data = await self._drive.download(f)
            if kind == "pdf":
                _, pages = extract_pdf(data)
            else:
                pages = extract_plain(data.decode("utf-8", errors="replace"))
            await self._indexer.index(s, doc, pages, title=f.name, remote_modified=f.modified_time, force=force)
            return []
        except Exception as exc:
            return [await self._fail(s, doc, exc)]

    async def _web_sync(self, s: AsyncSession, job: IngestJob) -> tuple[list[dict[str, str]], int]:
        payload, collection = dict(job.payload), job.collection
        urls = validate_urls(list(payload["urls"]))
        limit = min(int(payload.get("max_pages") or self._s.web_page_limit), self._s.web_page_limit)
        follow = bool(payload.get("follow_links"))
        queue, seen, failures, done = list(urls), set(urls), [], 0
        while queue and done < limit:
            url = queue.pop(0)
            doc = await self._indexer.get_or_create(s, collection, "WEB", url, url)
            try:
                if not await self._web.allowed(url):
                    raise ExtractionError("Blocked by robots.txt")
                page = await self._web.fetch(url)
                title, pages = extract_html(page.html)
                await self._indexer.index(s, doc, pages, title=title or url, force=bool(payload.get("force")))
                if follow:
                    for link in same_domain_links(page):
                        if link not in seen and len(seen) < limit * 3:
                            seen.add(link)
                            queue.append(link)
            except Exception as exc:
                failures.append(await self._fail(s, doc, exc))
            done += 1
            await self._progress(s, job, done, min(limit, max(len(queue) + done, 1)))
        return failures, done

    async def _reindex(self, s: AsyncSession, job: IngestJob) -> tuple[list[dict[str, str]], int]:
        collection = job.collection
        docs = list((await s.execute(select(Document).where(Document.collection == collection))).scalars())
        failures: list[dict[str, str]] = []
        for i, doc in enumerate(docs):
            try:
                if doc.source_type == "PDF":
                    _, pages = extract_pdf(upload_path(self._s, doc.collection, doc.id).read_bytes())
                elif doc.source_type == "WEB":
                    if not await self._web.allowed(doc.source_ref):
                        raise ExtractionError("Blocked by robots.txt")
                    _, pages = extract_html((await self._web.fetch(doc.source_ref)).html)
                else:
                    kind, data = await self._drive.download(DriveFile(doc.source_ref, doc.title, "", doc.remote_modified or ""))
                    pages = extract_pdf(data)[1] if kind == "pdf" else extract_plain(data.decode("utf-8", errors="replace"))
                await self._indexer.index(s, doc, pages, force=True)
            except Exception as exc:
                failures.append(await self._fail(s, doc, exc))
            await self._progress(s, job, i + 1, len(docs))
        return failures, len(docs)


def job_dict(job: IngestJob) -> dict[str, Any]:
    return {
        "id": job.id,
        "collection": job.collection,
        "kind": job.kind,
        "status": job.status,
        "progress": job.progress,
        "error": job.error,
        "failures": job.failures,
        "created_at": job.created_at.isoformat(),
        "finished_at": job.finished_at.isoformat() if job.finished_at else None,
    }
