"""Embedding providers. Vectors are normalised so cosine similarity is a dot product."""

import asyncio
import hashlib
import logging
import math
import re
from typing import Protocol

log = logging.getLogger(__name__)


class EmbeddingProvider(Protocol):
    dimension: int

    async def embed(self, texts: list[str]) -> list[list[float]]: ...


class SentenceTransformerEmbedder:
    """BAAI/bge-m3 through sentence-transformers, batched, loaded once in the app lifespan."""

    def __init__(self, model_name: str, batch_size: int, device: str = "auto") -> None:
        from sentence_transformers import SentenceTransformer  # heavy import, only when used

        if device == "auto":
            import torch

            device = "cuda" if torch.cuda.is_available() else "cpu"
        self.device = device
        self.batch_size = batch_size
        log.info("Loading embedding model %s on %s", model_name, device)
        self._model = SentenceTransformer(model_name, device=device)
        if device == "cuda":
            self._model.half()  # fp16 so bge-m3 fits small GPUs
        self.dimension = int(self._model.get_sentence_embedding_dimension() or 0)

    def _encode(self, texts: list[str]) -> list[list[float]]:
        out = self._model.encode(texts, batch_size=self.batch_size, normalize_embeddings=True, show_progress_bar=False)
        return [[float(x) for x in row] for row in out]

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        return await asyncio.to_thread(self._encode, texts)


class FakeEmbedder:
    """Deterministic bag-of-words hashing embedder: texts sharing words are close. For tests and the fake stack."""

    dimension = 256

    @staticmethod
    def _vec(text: str) -> list[float]:
        v = [0.0] * FakeEmbedder.dimension
        for word in re.findall(r"\w+", text.lower()):
            h = int(hashlib.md5(word.encode(), usedforsecurity=False).hexdigest(), 16)  # noqa: S324
            v[h % FakeEmbedder.dimension] += 1.0
        norm = math.sqrt(sum(x * x for x in v)) or 1.0
        return [x / norm for x in v]

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._vec(t) for t in texts]
