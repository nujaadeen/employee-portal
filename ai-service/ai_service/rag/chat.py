"""Chat orchestration: retrieve, build the prompt, stream an answer or produce a validated social post draft."""

import json
import logging
import re
import time
from collections.abc import AsyncIterator
from typing import Any

from pydantic import BaseModel, Field, ValidationError, field_validator

from ai_service.api.schemas import ChatRequest, ChatResponse, DraftOut, SourceOut
from ai_service.core.config import Settings
from ai_service.core.errors import ProblemError
from ai_service.providers.llm import LlmProvider, Message
from ai_service.providers.vectorstore import Hit
from ai_service.rag.prompts import DRAFT_SCHEMA, PLATFORM_LIMITS, build_context_block, build_messages
from ai_service.rag.retrieval import Retriever
from ai_service.rag.text import detect_language

log = logging.getLogger(__name__)
Event = tuple[str, dict[str, Any]]

_POST_INTENT = re.compile(
    r"\b(ad|ads|post|posts|caption|linkedin|instagram|facebook|social media|announcement|tweet|campaign copy)\b", re.IGNORECASE
)


def detect_intent(message: str) -> str:
    return "social_post" if _POST_INTENT.search(message) else "answer"


class DraftModel(BaseModel):
    headline: str = Field(min_length=1, max_length=200)
    caption: str = Field(min_length=1)
    hashtags: list[str] = Field(default_factory=list, max_length=15)
    platform: str
    language: str = Field(min_length=2, max_length=10)

    @field_validator("hashtags")
    @classmethod
    def _strip_hash(cls, v: list[str]) -> list[str]:
        return [h.lstrip("#").strip() for h in v if h.strip()]


def parse_draft(raw: str, platform: str) -> DraftModel:
    """Validate the model output against the schema and the platform limits. Raises ValueError when invalid."""
    text = raw.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text)
    try:
        draft = DraftModel.model_validate(json.loads(text))
    except (json.JSONDecodeError, ValidationError) as exc:
        raise ValueError("draft is not valid") from exc
    if len(draft.caption) > PLATFORM_LIMITS[platform]:
        raise ValueError("caption too long")
    return draft.model_copy(update={"platform": platform})


def _source_out(n: int, hit: Hit) -> SourceOut:
    page = hit.metadata.get("page")
    return SourceOut(
        index=n,
        title=str(hit.metadata.get("title", "")),
        collection=str(hit.metadata.get("collection", "")),
        page=int(page) if page is not None else None,
        source_ref=str(hit.metadata.get("source_ref", "")),
        score=round(hit.score, 4),
    )


class ChatService:
    def __init__(self, settings: Settings, retriever: Retriever, llm: LlmProvider) -> None:
        self._s, self._retriever, self._llm = settings, retriever, llm

    async def events(self, req: ChatRequest) -> AsyncIterator[Event]:
        started = time.perf_counter()
        intent = req.mode if req.mode != "auto" else detect_intent(req.message)
        language = req.language or detect_language(req.message)
        hits = await self._retriever.retrieve(req.message, req.allowed_collections)
        block, used = build_context_block(hits, self._s.retrieval_max_context_chars)
        sources = [_source_out(i + 1, h) for i, h in enumerate(used)]
        history = [(t.role, t.content) for t in req.history[-self._s.chat_history_turns :]]
        log.info(
            "chat user=%s conversation=%s collections=%s intent=%s sources=%d",
            req.user.id,
            req.conversation_id,
            ",".join(req.allowed_collections),
            intent,
            len(sources),
        )
        messages = build_messages(
            user_name=req.user.name,
            language=req.language,
            history=history,
            question=req.message,
            context_block=block,
            post_platform=req.platform if intent == "social_post" else None,
        )
        try:
            if intent == "social_post":
                draft = await self._draft(messages, req.platform)
                intro = f"Here is a draft for {req.platform.capitalize()}. Check the facts before you publish."
                yield "token", {"text": intro}
                yield "sources", {"sources": [s.model_dump() for s in sources]}
                out = DraftOut(**draft.model_dump(), sources=sources)
                yield "draft", {"draft": out.model_dump()}
            else:
                async for piece in self._llm.stream(
                    messages, temperature=self._s.llm_temperature, max_tokens=self._s.llm_max_tokens
                ):
                    yield "token", {"text": piece}
                yield "sources", {"sources": [s.model_dump() for s in sources]}
        except ProblemError as exc:
            yield "error", {"code": exc.code, "detail": exc.detail}
            return
        yield (
            "done",
            {
                "conversation_id": req.conversation_id,
                "intent": intent,
                "language": language,
                "elapsed_ms": int((time.perf_counter() - started) * 1000),
            },
        )

    async def _draft(self, messages: list[Message], platform: str) -> DraftModel:
        convo = list(messages)
        for attempt in (1, 2):
            raw = await self._llm.complete(
                convo, temperature=self._s.llm_temperature, max_tokens=self._s.llm_max_tokens, json_schema=DRAFT_SCHEMA
            )
            try:
                return parse_draft(raw, platform)
            except ValueError:
                if attempt == 2:
                    break
                convo = [
                    *messages,
                    Message(
                        "user",
                        "Your last reply was not valid. Reply again with ONLY the JSON object that matches the schema, and keep the caption within the length limit.",
                    ),
                ]
        raise ProblemError(502, "invalid-draft", "The model did not return a valid draft after one retry")

    async def respond(self, req: ChatRequest) -> ChatResponse:
        text_parts: list[str] = []
        sources: list[SourceOut] = []
        draft: DraftOut | None = None
        intent, language = "answer", req.language or "en"
        async for name, data in self.events(req):
            if name == "token":
                text_parts.append(data["text"])
            elif name == "sources":
                sources = [SourceOut(**s) for s in data["sources"]]
            elif name == "draft":
                draft = DraftOut(**data["draft"])
            elif name == "error":
                raise ProblemError(502, data["code"], data["detail"])
            elif name == "done":
                intent, language = data["intent"], data["language"]
        return ChatResponse(
            conversation_id=req.conversation_id,
            intent=intent,
            language=language,
            text="".join(text_parts).strip(),
            sources=sources,
            draft=draft,
        )
