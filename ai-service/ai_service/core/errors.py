"""RFC 7807 problem responses."""

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException


class ProblemError(Exception):
    def __init__(self, status: int, code: str, detail: str, headers: dict[str, str] | None = None) -> None:
        super().__init__(detail)
        self.status = status
        self.code = code
        self.detail = detail
        self.headers = headers or {}


def problem_body(status: int, code: str, detail: str, request: Request | None = None) -> dict[str, object]:
    body: dict[str, object] = {
        "type": f"https://baracel.example/problems/{code}",
        "title": code.replace("-", " ").capitalize(),
        "status": status,
        "detail": detail,
    }
    rid = getattr(request.state, "request_id", None) if request is not None else None
    if rid:
        body["requestId"] = rid
    return body


def problem_response(
    status: int, code: str, detail: str, request: Request | None = None, headers: dict[str, str] | None = None
) -> JSONResponse:
    return JSONResponse(
        problem_body(status, code, detail, request),
        status_code=status,
        media_type="application/problem+json",
        headers=headers,
    )


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(ProblemError)
    async def _problem(request: Request, exc: ProblemError) -> JSONResponse:
        return problem_response(exc.status, exc.code, exc.detail, request, exc.headers)

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        # Field names and error kinds only: never echo the submitted values (they may contain message text).
        fields = [f"{'.'.join(str(p) for p in e['loc'])}: {e['type']}" for e in exc.errors()]
        return problem_response(422, "validation-error", "; ".join(fields)[:500], request)

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        return problem_response(exc.status_code, f"http-{exc.status_code}", str(exc.detail), request)
