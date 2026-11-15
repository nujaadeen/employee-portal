"""Wiring of the providers and services. Tests build their own Services with fakes."""

from dataclasses import dataclass

from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker

from ai_service.core.config import Settings
from ai_service.db.session import make_engine, make_sessionmaker
from ai_service.jobs.worker import JobWorker
from ai_service.providers.embeddings import EmbeddingProvider, FakeEmbedder
from ai_service.providers.llm import LlmProvider, build_llm
from ai_service.providers.vectorstore import ChromaVectorStore, VectorStore
from ai_service.rag.chat import ChatService
from ai_service.rag.drive import DriveClient, build_drive
from ai_service.rag.ingest import Indexer
from ai_service.rag.retrieval import Reranker, Retriever
from ai_service.rag.web import HttpWebFetcher, WebFetcher


@dataclass
class Services:
    settings: Settings
    engine: AsyncEngine
    maker: async_sessionmaker[AsyncSession]
    embedder: EmbeddingProvider
    store: VectorStore
    llm: LlmProvider
    indexer: Indexer
    drive: DriveClient
    web: WebFetcher
    worker: JobWorker
    chat: ChatService


def assemble(
    settings: Settings,
    *,
    engine: AsyncEngine,
    embedder: EmbeddingProvider,
    store: VectorStore,
    llm: LlmProvider,
    drive: DriveClient,
    web: WebFetcher,
    reranker: Reranker | None = None,
) -> Services:
    maker = make_sessionmaker(engine)
    indexer = Indexer(settings, embedder, store)
    worker = JobWorker(settings, maker, indexer, drive, web)
    chat = ChatService(settings, Retriever(settings, embedder, store, reranker), llm)
    return Services(settings, engine, maker, embedder, store, llm, indexer, drive, web, worker, chat)


def build_services(settings: Settings) -> Services:
    embedder: EmbeddingProvider
    if settings.embedding_provider == "fake":
        embedder = FakeEmbedder()
    else:
        from ai_service.providers.embeddings import SentenceTransformerEmbedder

        embedder = SentenceTransformerEmbedder(settings.embedding_model, settings.embedding_batch_size, settings.embedding_device)
    store: VectorStore = (
        ChromaVectorStore.ephemeral() if settings.chroma_mode == "ephemeral" else ChromaVectorStore.from_url(settings.chroma_url)
    )
    reranker: Reranker | None = None
    if settings.reranker_enabled:
        from ai_service.rag.retrieval import CrossEncoderReranker

        reranker = CrossEncoderReranker(settings.reranker_model, settings.embedding_device)
    return assemble(
        settings,
        engine=make_engine(settings.database_url),
        embedder=embedder,
        store=store,
        llm=build_llm(settings),
        drive=build_drive(settings.google_sa_key_path),
        web=HttpWebFetcher(settings.web_user_agent, settings.web_allow_private),
        reranker=reranker,
    )
