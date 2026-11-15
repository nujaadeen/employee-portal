import re
from pathlib import Path
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, File, UploadFile
from fastapi import Path as PathParam
from sqlalchemy import func, select

from ai_service.api.deps import get_services
from ai_service.api.schemas import (
    CODE_PATTERN,
    CollectionOut,
    DocumentOut,
    DriveSourceIn,
    JobAccepted,
    JobOut,
    WebSourceIn,
)
from ai_service.container import Services
from ai_service.core.errors import ProblemError
from ai_service.core.security import require_scope
from ai_service.db.models import Document, IngestJob
from ai_service.jobs.worker import job_dict, upload_path
from ai_service.rag.extract import PDF_MAGIC
from ai_service.rag.web import validate_urls

router = APIRouter(prefix="/v1", tags=["ingestion"], dependencies=[Depends(require_scope("ai:ingest"))])
Code = Annotated[str, PathParam(pattern=CODE_PATTERN)]
CHUNK = 1024 * 1024


def _doc_out(d: Document) -> DocumentOut:
    return DocumentOut(
        id=d.id,
        collection=d.collection,
        source_type=d.source_type,
        source_ref=d.source_ref,
        title=d.title,
        status=d.status,
        error=d.error,
        chunk_count=d.chunk_count,
        updated_at=d.updated_at.isoformat(),
    )


async def _new_job(svc: Services, collection: str, kind: str, payload: dict[str, object]) -> IngestJob:
    async with svc.maker() as s:
        job = IngestJob(collection=collection, kind=kind, payload=payload, failures=[])
        s.add(job)
        await s.commit()
    return job


def _safe_name(name: str | None) -> str:
    base = Path(name or "document.pdf").name
    return re.sub(r"[^A-Za-z0-9._ -]", "_", base)[:200] or "document.pdf"


@router.post("/collections/{code}/documents", status_code=202, response_model=JobAccepted)
async def upload_pdf(code: Code, file: Annotated[UploadFile, File()], svc: Services = Depends(get_services)) -> JobAccepted:
    if file.content_type not in ("application/pdf", "application/x-pdf"):
        raise ProblemError(415, "unsupported-media-type", "Only PDF files are accepted")
    limit = svc.settings.max_upload_bytes
    data = bytearray()
    while chunk := await file.read(CHUNK):
        data += chunk
        if len(data) > limit:
            raise ProblemError(413, "file-too-large", f"The file is larger than {svc.settings.ai_max_upload_mb} MB")
    if not data.startswith(PDF_MAGIC):
        raise ProblemError(415, "not-a-pdf", "The file content is not a PDF")
    name = _safe_name(file.filename)
    async with svc.maker() as s:
        doc = await svc.indexer.get_or_create(s, code, "PDF", name, name)
        path = upload_path(svc.settings, code, doc.id)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(bytes(data))
        doc_id = doc.id
    job = await _new_job(svc, code, "PDF", {"document_id": doc_id})
    svc.worker.enqueue(job.id)
    return JobAccepted(job_id=job.id, document_id=doc_id)


@router.post("/collections/{code}/sources/drive", status_code=202, response_model=JobAccepted)
async def sync_drive(code: Code, body: DriveSourceIn, svc: Services = Depends(get_services)) -> JobAccepted:
    job = await _new_job(svc, code, "DRIVE", body.model_dump())
    svc.worker.enqueue(job.id)
    return JobAccepted(job_id=job.id)


@router.post("/collections/{code}/sources/web", status_code=202, response_model=JobAccepted)
async def sync_web(code: Code, body: WebSourceIn, svc: Services = Depends(get_services)) -> JobAccepted:
    urls = validate_urls(body.urls)  # same domain, http(s) only: rejected before a job exists
    job = await _new_job(svc, code, "WEB", {**body.model_dump(), "urls": urls})
    svc.worker.enqueue(job.id)
    return JobAccepted(job_id=job.id)


@router.post("/collections/{code}/reindex", status_code=202, response_model=JobAccepted)
async def reindex(code: Code, svc: Services = Depends(get_services)) -> JobAccepted:
    async with svc.maker() as s:
        count = (await s.execute(select(func.count()).select_from(Document).where(Document.collection == code))).scalar_one()
    if count == 0:
        raise ProblemError(404, "collection-not-found", "The collection has no documents")
    job = await _new_job(svc, code, "REINDEX", {})
    svc.worker.enqueue(job.id)
    return JobAccepted(job_id=job.id)


@router.get("/collections", response_model=list[CollectionOut])
async def list_collections(svc: Services = Depends(get_services)) -> list[CollectionOut]:
    async with svc.maker() as s:
        docs = (await s.execute(select(Document))).scalars().all()
        syncs = dict(
            (await s.execute(select(IngestJob.collection, func.max(IngestJob.finished_at)).group_by(IngestJob.collection))).all()
        )
    out: list[CollectionOut] = []
    for code in sorted({d.collection for d in docs}):
        mine = [d for d in docs if d.collection == code]
        statuses = {d.status for d in mine}
        status: Literal["EMPTY", "READY", "INDEXING", "FAILED"] = (
            "INDEXING" if statuses & {"PENDING", "INDEXING"} else ("FAILED" if statuses == {"FAILED"} else "READY")
        )
        last = syncs.get(code)
        out.append(
            CollectionOut(
                code=code,
                documents=len(mine),
                chunks=sum(d.chunk_count for d in mine),
                last_sync=last.isoformat() if last else None,
                status=status,
            )
        )
    return out


@router.get("/collections/{code}/documents", response_model=list[DocumentOut])
async def list_documents(code: Code, svc: Services = Depends(get_services)) -> list[DocumentOut]:
    async with svc.maker() as s:
        docs = (await s.execute(select(Document).where(Document.collection == code).order_by(Document.title))).scalars()
        return [_doc_out(d) for d in docs]


@router.delete("/collections/{code}/documents/{document_id}", status_code=204)
async def delete_document(code: Code, document_id: str, svc: Services = Depends(get_services)) -> None:
    async with svc.maker() as s:
        doc = await s.get(Document, document_id)
        if doc is None or doc.collection != code:
            raise ProblemError(404, "document-not-found", "No such document in this collection")
        await svc.indexer.remove(s, doc)
    upload_path(svc.settings, code, document_id).unlink(missing_ok=True)


@router.get("/jobs/{job_id}", response_model=JobOut)
async def get_job(job_id: str, svc: Services = Depends(get_services)) -> JobOut:
    async with svc.maker() as s:
        job = await s.get(IngestJob, job_id)
        if job is None:
            raise ProblemError(404, "job-not-found", "No such job")
        return JobOut(**job_dict(job))
