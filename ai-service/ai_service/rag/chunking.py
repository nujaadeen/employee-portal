"""Split page texts into chunks of about N tokens with overlap, on paragraph boundaries, keeping page numbers."""

import re
from dataclasses import dataclass

from ai_service.rag.text import estimate_tokens


@dataclass(frozen=True)
class Page:
    number: int
    text: str


@dataclass(frozen=True)
class Chunk:
    text: str
    page: int
    index: int


@dataclass(frozen=True)
class _Unit:
    text: str
    page: int
    tokens: int


def _split_oversize(text: str, max_tokens: int) -> list[str]:
    """Split a paragraph that is too big: by sentence, then by words."""
    if estimate_tokens(text) <= max_tokens:
        return [text]
    pieces: list[str] = []
    current = ""
    for sentence in re.split(r"(?<=[.!?])\s+", text):
        for part in _split_words(sentence, max_tokens):
            if current and estimate_tokens(current + " " + part) > max_tokens:
                pieces.append(current)
                current = part
            else:
                current = f"{current} {part}".strip()
    if current:
        pieces.append(current)
    return pieces


def _split_words(text: str, max_tokens: int) -> list[str]:
    if estimate_tokens(text) <= max_tokens:
        return [text]
    words, out, cur = text.split(), [], ""
    for w in words:
        if cur and estimate_tokens(cur + " " + w) > max_tokens:
            out.append(cur)
            cur = w
        else:
            cur = f"{cur} {w}".strip()
    if cur:
        out.append(cur)
    return out


def chunk_pages(pages: list[Page], max_tokens: int = 500, overlap_tokens: int = 80) -> list[Chunk]:
    units: list[_Unit] = []
    for page in pages:
        for para in re.split(r"\n\s*\n", page.text):
            para = para.strip()
            if not para:
                continue
            for piece in _split_oversize(para, max_tokens):
                units.append(_Unit(piece, page.number, estimate_tokens(piece)))

    chunks: list[Chunk] = []
    window: list[_Unit] = []
    total = 0
    fresh = 0  # units in the window that are not overlap from the previous chunk
    for unit in units:
        if window and total + unit.tokens > max_tokens:
            chunks.append(_make(window, len(chunks)))
            window, total = _overlap(window, overlap_tokens)
            fresh = 0
            if total + unit.tokens > max_tokens:  # never exceed the limit just to keep overlap
                window, total = [], 0
        window.append(unit)
        total += unit.tokens
        fresh += 1
    if window and fresh:
        chunks.append(_make(window, len(chunks)))
    return chunks


def _overlap(window: list[_Unit], overlap_tokens: int) -> tuple[list[_Unit], int]:
    kept: list[_Unit] = []
    total = 0
    for unit in reversed(window):
        if total + unit.tokens > overlap_tokens:
            break
        kept.insert(0, unit)
        total += unit.tokens
    return kept, total


def _make(window: list[_Unit], index: int) -> Chunk:
    return Chunk(text="\n\n".join(u.text for u in window), page=window[0].page, index=index)
