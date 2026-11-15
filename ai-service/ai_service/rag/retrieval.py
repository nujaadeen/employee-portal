"""Retrieval. The collection filter is applied inside the vector query. An empty list returns no context."""

import asyncio
import logging
from typing import Any, Protocol

from ai_service.core.config import Settings
from ai_service.providers.embeddings import EmbeddingProvider
from ai_service.providers.vectorstore import Hit, VectorStore

log = logging.getLogger(__name__)


class Reranker(Protocol):
    async def rerank(self, query: str, hits: list[Hit]) -> list[Hit]: ...


class CrossEncoderReranker:
    def __init__(self, model_name: str, device: str = "auto") -> None:
        from sentence_transformers import CrossEncoder

        if device == "auto":
            import torch

            device = "cuda" if torch.cuda.is_available() else "cpu"
        self._model: Any = CrossEncoder(model_name, device=device)

    async def rerank(self, query: str, hits: list[Hit]) -> list[Hit]:
        if not hits:
            return hits
        scores = await asyncio.to_thread(lambda: self._model.predict([(query, h.text) for h in hits]))
        order = sorted(zip(hits, scores, strict=True), key=lambda p: float(p[1]), reverse=True)
        return [h for h, _ in order]


class Retriever:
    def __init__(
        self, settings: Settings, embedder: EmbeddingProvider, store: VectorStore, reranker: Reranker | None = None
    ) -> None:
        self._s, self._embedder, self._store, self._reranker = settings, embedder, store, reranker

    async def retrieve(self, query: str, allowed_collections: list[str]) -> list[Hit]:
        if not allowed_collections:
            return []  # no collections, no context: the store is not even queried
        [vector] = await self._embedder.embed([query])
        fetch = self._s.retrieval_top_k * (3 if self._reranker else 1)
        hits = await self._store.query(vector, allowed_collections, fetch)
        # Defence in depth: the filter already ran inside the query. A hit outside the list is a bug, never output.
        allowed = set(allowed_collections)
        safe = [h for h in hits if h.metadata.get("collection") in allowed]
        if len(safe) != len(hits):
            log.error("retrieval returned %d hits outside the allowed collections; dropped", len(hits) - len(safe))
        safe = [h for h in safe if h.score >= self._s.retrieval_min_score]
        if self._reranker:
            safe = await self._reranker.rerank(query, safe)
        return safe[: self._s.retrieval_top_k]
