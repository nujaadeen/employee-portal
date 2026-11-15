"""Configuration, only from environment variables (and an optional local .env that is never committed)."""

from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Database
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5433/ai_service"

    # Keycloak (service tokens from the portal-backend-ai client)
    oidc_issuer: str = "http://localhost:8180/realms/baracel"
    oidc_jwks_url: str = ""  # defaults to <issuer>/protocol/openid-connect/certs
    oidc_audience: str = "ai-service"
    oidc_leeway_seconds: int = 30

    # Vector store
    chroma_url: str = "http://localhost:8001"
    chroma_mode: Literal["http", "ephemeral"] = "http"

    # Embeddings
    embedding_provider: Literal["sentence_transformers", "fake"] = "sentence_transformers"
    embedding_model: str = "BAAI/bge-m3"
    embedding_batch_size: int = 16
    embedding_device: str = "auto"  # auto, cuda or cpu
    reranker_enabled: bool = False
    reranker_model: str = "BAAI/bge-reranker-v2-m3"

    # LLM
    ai_llm_provider: Literal["ollama", "openai_compatible", "fake"] = "ollama"
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:7b-instruct"
    openai_base_url: str = "https://api.openai.com/v1"
    openai_api_key: str = ""
    openai_model: str = ""
    llm_temperature: float = 0.3
    llm_max_tokens: int = 1024
    llm_timeout_seconds: float = 180.0

    # Retrieval
    retrieval_top_k: int = 8
    retrieval_min_score: float = 0.30  # cosine similarity (1 - distance)
    retrieval_max_context_chars: int = 9000
    chat_history_turns: int = 6

    # Ingestion
    ai_max_upload_mb: int = 25
    chunk_tokens: int = 500
    chunk_overlap_tokens: int = 80
    web_page_limit: int = 30
    web_user_agent: str = "BaracelAiBot/1.0"
    google_sa_key_path: str = ""
    upload_dir: str = "./data/uploads"
    web_allow_private: bool = False  # dev only: allow crawling private addresses

    log_level: str = "INFO"
    run_worker: bool = True

    @property
    def jwks_url(self) -> str:
        return self.oidc_jwks_url or f"{self.oidc_issuer.rstrip('/')}/protocol/openid-connect/certs"

    @property
    def max_upload_bytes(self) -> int:
        return self.ai_max_upload_mb * 1024 * 1024


Collection = Field(pattern=r"^[a-z][a-z0-9_-]{1,39}$")


@lru_cache
def get_settings() -> Settings:
    return Settings()
