"""Text cleaning, token estimate and language detection."""

import re

from langdetect import DetectorFactory, LangDetectException, detect

DetectorFactory.seed = 0
_CTRL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")


def clean_text(text: str) -> str:
    text = _CTRL.sub("", text.replace("\r\n", "\n").replace("\r", "\n"))
    text = re.sub(r"(\w)-\n(\w)", r"\1\2", text)  # join words hyphenated at a line break
    text = re.sub(r"[ \t ]+", " ", text)
    text = re.sub(r" ?\n ?", "\n", text)
    text = re.sub(r"(?<!\n)\n(?!\n)", " ", text)  # single newlines are soft wraps
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def estimate_tokens(text: str) -> int:
    """Cheap estimate that works for any model: about 4 characters per token."""
    return max(1, (len(text) + 3) // 4)


def detect_language(text: str) -> str:
    try:
        return str(detect(text[:2000]))
    except LangDetectException:
        return "en"
