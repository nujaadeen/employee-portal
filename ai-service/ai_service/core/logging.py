"""Logging. Never log message text, document text or tokens: only ids, codes, counts and timings."""

import logging
import sys
from contextvars import ContextVar

request_id_var: ContextVar[str] = ContextVar("request_id", default="-")


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = request_id_var.get()
        return True


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s [%(request_id)s] %(name)s: %(message)s"))
    handler.addFilter(RequestIdFilter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level.upper())
    for noisy in ("httpx", "httpcore", "chromadb", "urllib3"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
