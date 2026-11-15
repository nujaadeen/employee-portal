"""Ingestion pipeline: extract, clean, chunk, detect language, embed in batches, upsert.

Re-ingesting a changed document replaces its old chunks. An unchanged content hash is skipped.
Logs carry ids, collection codes and counts only, never document text.
"""

import hashlib
import logging
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ai_service.core.config import Settings
from ai_service.db.models import Document
from ai_service.providers.embeddings import EmbeddingProvider
from ai_service.providers.vectorstore import ChunkRecord, VectorStore
from ai_service.rag.chunking import Page, chunk_pages
from ai_service.rag.text import detect_language

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class IndexResult:
    document: Document
    skipped: bool


def hash_pages(pages: list[Page]) -> str:
    h = hashlib.sha256()
    for p in pages:
        h.update(f"{p.number}\n{p.text}\n".encode())
    return h.hexdigest()


class Indexer:
    def __init__(self, settings: Settings, embedder: EmbeddingProvider, store: VectorStore) -> None:
        self._s = settings
        self._embedder = embedder
        self._store = store

    async def get_or_create(
        self, session: AsyncSession, collection: str, source_type: str, source_ref: str, title: str
    ) -> Document:
        doc = (
            await session.execute(select(Document).where(Document.collection == collection, Document.source_ref == source_ref))
        ).scalar_one_or_none()
        if doc is None:
            doc = Document(collection=collection, source_type=source_type, source_ref=source_ref, title=title)
            session.add(doc)
            await session.commit()  # committed now so a later failure can still be recorded on it
        return doc

    async def index(
        self,
        session: AsyncSession,
        doc: Document,
        pages: list[Page],
        *,
        title: str | None = None,
        remote_modified: str | None = None,
        force: bool = False,
    ) -> IndexResult:
        digest = hash_pages(pages)
        if title:
            doc.title = title[:500]
        if remote_modified is not None:
            doc.remote_modified = remote_modified
        if not force and doc.status == "READY" and doc.content_hash == digest:
            await session.commit()
            log.info("document %s unchanged, skipped", doc.id)
            return IndexResult(doc, True)

        doc.status = "INDEXING"
        doc.error = None
        await session.commit()
        chunks = chunk_pages(pages, self._s.chunk_tokens, self._s.chunk_overlap_tokens)
        language = detect_language(" ".join(p.text for p in pages[:3]))
        stamp = datetime.now(UTC).isoformat()
        vectors: list[list[float]] = []
        batch = max(1, self._s.embedding_batch_size)
        for i in range(0, len(chunks), batch):
            vectors.extend(await self._embedder.embed([c.text for c in chunks[i : i + batch]]))
        records = [
            ChunkRecord(
                id=f"{doc.id}:{c.index}",
                text=c.text,
                embedding=vec,
                metadata={
                    "collection": doc.collection,
                    "document_id": doc.id,
                    "source_type": doc.source_type,
                    "source_ref": doc.source_ref,
                    "title": doc.title,
                    "page": c.page,
                    "chunk_index": c.index,
                    "language": language,
                    "updated_at": stamp,
                },
            )
            for c, vec in zip(chunks, vectors, strict=True)
        ]
        # replace: remove the old chunks, then write the new ones
        await self._store.delete_document(doc.collection, doc.id)
        await self._store.upsert(doc.collection, records)
        doc.content_hash = digest
        doc.chunk_count = len(records)
        doc.status = "READY"
        await session.commit()
        log.info("document %s indexed: collection=%s chunks=%d", doc.id, doc.collection, len(records))
        return IndexResult(doc, False)

    async def remove(self, session: AsyncSession, doc: Document) -> None:
        await self._store.delete_document(doc.collection, doc.id)
        await session.delete(doc)
        await session.commit()
