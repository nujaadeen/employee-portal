"""Text extraction from PDFs (PyMuPDF, per page) and web pages (trafilatura)."""

import pymupdf
import trafilatura

from ai_service.rag.chunking import Page
from ai_service.rag.text import clean_text

PDF_MAGIC = b"%PDF-"


class ExtractionError(Exception):
    pass


def extract_pdf(data: bytes) -> tuple[str, list[Page]]:
    """Returns (title, pages). Pages with no text are skipped (scanned pages need OCR, which is not done here)."""
    if not data.startswith(PDF_MAGIC):
        raise ExtractionError("Not a PDF file")
    try:
        doc = pymupdf.open(stream=data, filetype="pdf")  # type: ignore[no-untyped-call]
    except Exception as exc:
        raise ExtractionError("The PDF could not be opened") from exc
    try:
        if doc.needs_pass:
            raise ExtractionError("The PDF is password protected")
        title = (doc.metadata or {}).get("title") or ""
        pages = [
            Page(i + 1, clean_text(str(doc[i].get_text("text"))))  # type: ignore[no-untyped-call]
            for i in range(doc.page_count)
        ]
    finally:
        doc.close()  # type: ignore[no-untyped-call]
    pages = [p for p in pages if p.text]
    if not pages:
        raise ExtractionError("The PDF has no extractable text")
    return title.strip(), pages


def extract_html(html: str) -> tuple[str, list[Page]]:
    text = trafilatura.extract(html, include_comments=False, include_tables=True, favor_recall=True) or ""
    text = clean_text(text)
    if not text:
        raise ExtractionError("The page has no readable text")
    meta = trafilatura.extract_metadata(html)
    return (meta.title if meta and meta.title else "").strip(), [Page(1, text)]


def extract_plain(text: str) -> list[Page]:
    cleaned = clean_text(text)
    if not cleaned:
        raise ExtractionError("The document is empty")
    return [Page(1, cleaned)]
