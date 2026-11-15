"""LLM providers behind one interface: Ollama, any OpenAI-compatible API, and a deterministic fake."""

import json
import re
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any, Protocol

import httpx

from ai_service.core.config import Settings
from ai_service.core.errors import ProblemError


@dataclass(frozen=True)
class Message:
    role: str  # system, user or assistant
    content: str


class LlmProvider(Protocol):
    name: str

    async def ping(self) -> bool: ...

    def stream(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> AsyncIterator[str]: ...

    async def complete(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> str: ...


class _Base:
    name = "base"

    def stream(  # pragma: no cover - overridden
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> AsyncIterator[str]:
        raise NotImplementedError

    async def complete(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> str:
        parts = [t async for t in self.stream(messages, temperature=temperature, max_tokens=max_tokens, json_schema=json_schema)]
        return "".join(parts)


def _unavailable(exc: Exception) -> ProblemError:
    # Do not include request bodies or message text in the error.
    return ProblemError(502, "llm-unavailable", f"The language model could not be reached ({type(exc).__name__})")


class OllamaProvider(_Base):
    name = "ollama"

    def __init__(self, url: str, model: str, timeout: float) -> None:
        self._url = url.rstrip("/")
        self._model = model
        self._timeout = timeout

    async def ping(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=3.0) as c:
                return (await c.get(f"{self._url}/api/tags")).status_code == 200
        except httpx.HTTPError:
            return False

    async def stream(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> AsyncIterator[str]:
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "stream": True,
            "options": {"temperature": temperature, "num_predict": max_tokens},
        }
        if json_schema is not None:
            payload["format"] = json_schema
        try:
            async with (
                httpx.AsyncClient(timeout=httpx.Timeout(self._timeout, connect=5.0)) as c,
                c.stream("POST", f"{self._url}/api/chat", json=payload) as resp,
            ):
                if resp.status_code != 200:
                    raise ProblemError(502, "llm-error", f"Ollama answered {resp.status_code}")
                async for line in resp.aiter_lines():
                    if not line:
                        continue
                    data = json.loads(line)
                    piece = data.get("message", {}).get("content", "")
                    if piece:
                        yield piece
                    if data.get("done"):
                        break
        except httpx.HTTPError as exc:
            raise _unavailable(exc) from exc


class OpenAICompatibleProvider(_Base):
    name = "openai_compatible"

    def __init__(self, base_url: str, api_key: str, model: str, timeout: float) -> None:
        self._base = base_url.rstrip("/")
        self._key = api_key
        self._model = model
        self._timeout = timeout

    async def ping(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=3.0) as c:
                r = await c.get(f"{self._base}/models", headers={"Authorization": f"Bearer {self._key}"})
                return r.status_code < 500
        except httpx.HTTPError:
            return False

    async def stream(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> AsyncIterator[str]:
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "stream": True,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if json_schema is not None:
            payload["response_format"] = {
                "type": "json_schema",
                "json_schema": {"name": "result", "schema": json_schema, "strict": False},
            }
        try:
            async with (
                httpx.AsyncClient(timeout=httpx.Timeout(self._timeout, connect=5.0)) as c,
                c.stream(
                    "POST",
                    f"{self._base}/chat/completions",
                    json=payload,
                    headers={"Authorization": f"Bearer {self._key}"},
                ) as resp,
            ):
                if resp.status_code != 200:
                    raise ProblemError(502, "llm-error", f"The model API answered {resp.status_code}")
                async for line in resp.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    body = line[5:].strip()
                    if body == "[DONE]":
                        break
                    choices = json.loads(body).get("choices") or [{}]
                    piece = (choices[0].get("delta") or {}).get("content")
                    if piece:
                        yield piece
        except httpx.HTTPError as exc:
            raise _unavailable(exc) from exc


_SOURCE_BLOCK = re.compile(r"^\[(\d+)\] (.+?) \(", re.MULTILINE)


class FakeLlm(_Base):
    """Deterministic. Answers from the first retrieved source and always cites the sources it was given.

    It reads the sources from the delimited data block of the last user message, exactly where the real prompt puts
    them. It never follows instructions found in that block. Drafts are valid JSON citing the retrieved sources.
    """

    name = "fake"

    def __init__(self) -> None:
        self.calls: list[list[Message]] = []
        self.invalid_draft_replies = 0  # tests set this to make the first N draft replies invalid JSON

    async def ping(self) -> bool:
        return True

    async def stream(
        self, messages: list[Message], *, temperature: float, max_tokens: int, json_schema: dict[str, Any] | None = None
    ) -> AsyncIterator[str]:
        self.calls.append(messages)
        last = messages[-1].content
        titles = _SOURCE_BLOCK.findall(last)
        if json_schema is not None and "headline" in json.dumps(json_schema):
            if self.invalid_draft_replies > 0:
                self.invalid_draft_replies -= 1
                text = "{ this is not json"
            else:
                text = json.dumps(self._draft(titles, last))
        elif titles:
            snippet = re.sub(r"\s+", " ", last.split("\n", 2)[-1])[:0]
            refs = " ".join(f"[{n}]" for n, _ in titles[:2])
            text = f"Here is what the sources say about this. {refs}{snippet}"
        else:
            text = "I do not know: the sources I can use do not cover this."
        for word in re.findall(r"\S+\s*", text):
            yield word

    @staticmethod
    def _draft(titles: list[tuple[str, str]], prompt: str) -> dict[str, Any]:
        cited = ", ".join(t for _, t in titles[:3]) or "no sources"
        platform = "LINKEDIN"
        m = re.search(r"platform: (\w+)", prompt)
        if m:
            platform = m.group(1)
        return {
            "headline": "Baracel: made simple",
            "caption": f"A short update from Baracel, based on {cited}. " + "Plain words, warm tone. " * 6,
            "hashtags": ["Baracel", "Teamwork"],
            "platform": platform,
            "language": "en",
        }


def build_llm(settings: Settings) -> LlmProvider:
    match settings.ai_llm_provider:
        case "ollama":
            return OllamaProvider(settings.ollama_url, settings.ollama_model, settings.llm_timeout_seconds)
        case "openai_compatible":
            return OpenAICompatibleProvider(
                settings.openai_base_url, settings.openai_api_key, settings.openai_model, settings.llm_timeout_seconds
            )
        case "fake":
            return FakeLlm()
    raise ValueError(settings.ai_llm_provider)  # pragma: no cover
