"""Vector store behind an interface. Chroma: one Chroma collection per portal collection code, cosine distance."""

import asyncio
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.parse import urlparse

import chromadb
from chromadb.config import Settings as ChromaSettings


@dataclass(frozen=True)
class ChunkRecord:
    id: str
    text: str
    embedding: list[float]
    metadata: dict[str, str | int | float | bool]


@dataclass(frozen=True)
class Hit:
    id: str
    text: str
    score: float  # cosine similarity, 1 is identical
    metadata: dict[str, Any]


class VectorStore(Protocol):
    async def ping(self) -> bool: ...
    async def upsert(self, collection: str, chunks: list[ChunkRecord]) -> None: ...
    async def delete_document(self, collection: str, document_id: str) -> None: ...
    async def count(self, collection: str) -> int: ...
    async def query(self, embedding: list[float], allowed_collections: list[str], top_k: int) -> list[Hit]: ...


class ChromaVectorStore:
    def __init__(self, client: Any) -> None:
        self._client = client

    @classmethod
    def from_url(cls, url: str) -> "ChromaVectorStore":
        u = urlparse(url)
        return cls(chromadb.HttpClient(host=u.hostname or "localhost", port=u.port or 8000, ssl=u.scheme == "https"))

    @classmethod
    def ephemeral(cls) -> "ChromaVectorStore":
        return cls(chromadb.EphemeralClient(settings=ChromaSettings(allow_reset=True, anonymized_telemetry=False)))

    def reset(self) -> None:
        """Tests only (needs an ephemeral client)."""
        self._client.reset()

    def _col(self, code: str) -> Any:
        return self._client.get_or_create_collection(name=code, metadata={"hnsw:space": "cosine"})

    async def ping(self) -> bool:
        return bool(await asyncio.to_thread(self._client.heartbeat))

    async def upsert(self, collection: str, chunks: list[ChunkRecord]) -> None:
        if not chunks:
            return

        def run() -> None:
            self._col(collection).upsert(
                ids=[c.id for c in chunks],
                documents=[c.text for c in chunks],
                embeddings=[c.embedding for c in chunks],
                metadatas=[c.metadata for c in chunks],
            )

        await asyncio.to_thread(run)

    async def delete_document(self, collection: str, document_id: str) -> None:
        await asyncio.to_thread(lambda: self._col(collection).delete(where={"document_id": document_id}))

    async def count(self, collection: str) -> int:
        return int(await asyncio.to_thread(lambda: self._col(collection).count()))

    async def query(self, embedding: list[float], allowed_collections: list[str], top_k: int) -> list[Hit]:
        # One Chroma collection per code: the allowed list decides which collections are even opened.
        # Nothing outside the list is queried, so there is no post-filtering step.
        if not allowed_collections:
            return []

        def run() -> list[Hit]:
            hits: list[Hit] = []
            existing = {c if isinstance(c, str) else c.name for c in self._client.list_collections()}
            for code in allowed_collections:
                if code not in existing:
                    continue
                col = self._col(code)
                if col.count() == 0:
                    continue
                res = col.query(
                    query_embeddings=[embedding],
                    n_results=min(top_k, col.count()),
                    where={"collection": {"$in": allowed_collections}},  # defence in depth inside the query
                    include=["documents", "metadatas", "distances"],
                )
                for i, cid in enumerate(res["ids"][0]):
                    hits.append(
                        Hit(
                            id=cid,
                            text=res["documents"][0][i],
                            score=1.0 - float(res["distances"][0][i]),
                            metadata=dict(res["metadatas"][0][i]),
                        )
                    )
            hits.sort(key=lambda h: h.score, reverse=True)
            return hits[:top_k]

        return await asyncio.to_thread(run)
