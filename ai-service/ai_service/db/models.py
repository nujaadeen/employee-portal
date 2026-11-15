import uuid
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import JSON, DateTime, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


def now() -> datetime:
    return datetime.now(UTC)


def new_id() -> str:
    return str(uuid.uuid4())


class Base(DeclarativeBase):
    pass


class Document(Base):
    __tablename__ = "document"
    __table_args__ = (
        UniqueConstraint("collection", "source_ref", name="uq_document_source"),
        Index("ix_document_collection", "collection"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    collection: Mapped[str] = mapped_column(String(40))
    source_type: Mapped[str] = mapped_column(String(10))  # PDF, DRIVE or WEB
    source_ref: Mapped[str] = mapped_column(String(1000))  # file name, Drive file id or URL
    title: Mapped[str] = mapped_column(String(500))
    content_hash: Mapped[str] = mapped_column(String(64), default="")
    status: Mapped[str] = mapped_column(String(10), default="PENDING")  # PENDING, INDEXING, READY, FAILED
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    chunk_count: Mapped[int] = mapped_column(Integer, default=0)
    remote_modified: Mapped[str | None] = mapped_column(String(40), nullable=True)  # Drive modifiedTime, for incremental sync
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now)


class IngestJob(Base):
    __tablename__ = "ingest_job"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    collection: Mapped[str] = mapped_column(String(40))
    kind: Mapped[str] = mapped_column(String(20))  # PDF, DRIVE, WEB, REINDEX
    status: Mapped[str] = mapped_column(String(10), default="QUEUED")  # QUEUED, RUNNING, DONE, PARTIAL, FAILED
    progress: Mapped[int] = mapped_column(Integer, default=0)  # 0 to 100
    error: Mapped[str | None] = mapped_column(Text, nullable=True)
    payload: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict)  # parameters, never document text
    failures: Mapped[list[Any]] = mapped_column(JSON, default=list)  # [{source_ref, error}] for files that failed
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
