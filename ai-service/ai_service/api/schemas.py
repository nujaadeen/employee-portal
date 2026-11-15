from typing import Literal

from pydantic import BaseModel, Field, field_validator

CODE_PATTERN = r"^[a-z][a-z0-9_-]{1,39}$"


class Turn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=8000)


class ChatUser(BaseModel):
    id: str = Field(max_length=64)
    name: str = Field(max_length=200)
    roles: list[str] = Field(default_factory=list, max_length=30)


class ChatRequest(BaseModel):
    conversation_id: str | None = Field(default=None, max_length=64)
    message: str = Field(min_length=1, max_length=8000)
    history: list[Turn] = Field(default_factory=list, max_length=50)
    user: ChatUser
    allowed_collections: list[str] = Field(default_factory=list, max_length=50)
    mode: Literal["auto", "answer", "social_post"] = "auto"
    language: str | None = Field(default=None, pattern=r"^[a-z]{2,3}(-[A-Za-z]{2,4})?$")
    platform: Literal["LINKEDIN", "INSTAGRAM", "FACEBOOK"] = "LINKEDIN"

    @field_validator("allowed_collections")
    @classmethod
    def _codes(cls, v: list[str]) -> list[str]:
        import re

        for code in v:
            if not re.match(CODE_PATTERN, code):
                raise ValueError("invalid collection code")
        return list(dict.fromkeys(v))


class SourceOut(BaseModel):
    index: int
    title: str
    collection: str
    page: int | None
    source_ref: str
    score: float


class DraftOut(BaseModel):
    headline: str
    caption: str
    hashtags: list[str]
    platform: str
    language: str
    sources: list[SourceOut]


class ChatResponse(BaseModel):
    conversation_id: str | None
    intent: Literal["answer", "social_post"]
    language: str
    text: str
    sources: list[SourceOut]
    draft: DraftOut | None = None


class DocumentOut(BaseModel):
    id: str
    collection: str
    source_type: str
    source_ref: str
    title: str
    status: str
    error: str | None
    chunk_count: int
    updated_at: str


class CollectionOut(BaseModel):
    code: str
    documents: int
    chunks: int
    last_sync: str | None
    status: Literal["EMPTY", "READY", "INDEXING", "FAILED"]


class JobOut(BaseModel):
    id: str
    collection: str
    kind: str
    status: str
    progress: int
    error: str | None
    failures: list[dict[str, str]]
    created_at: str
    finished_at: str | None


class JobAccepted(BaseModel):
    job_id: str
    document_id: str | None = None
    status: str = "QUEUED"


class DriveSourceIn(BaseModel):
    folder_id: str = Field(min_length=5, max_length=200, pattern=r"^[A-Za-z0-9_-]+$")
    recursive: bool = True
    force: bool = False


class WebSourceIn(BaseModel):
    urls: list[str] = Field(min_length=1, max_length=50)
    follow_links: bool = False
    max_pages: int | None = Field(default=None, ge=1, le=200)
    force: bool = False
