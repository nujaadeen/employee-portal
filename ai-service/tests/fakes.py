from ai_service.core.errors import ProblemError
from ai_service.providers.embeddings import FakeEmbedder
from ai_service.rag.drive import GOOGLE_DOC, PDF, DriveFile
from ai_service.rag.web import FetchedPage


class CountingEmbedder(FakeEmbedder):
    def __init__(self) -> None:
        self.texts_embedded = 0

    async def embed(self, texts: list[str]) -> list[list[float]]:
        self.texts_embedded += len(texts)
        return await super().embed(texts)


class FakeDrive:
    def __init__(self) -> None:
        self.files: dict[str, tuple[DriveFile, str, bytes]] = {}
        self.downloads: list[str] = []
        self.fail_ids: set[str] = set()

    def add(self, fid: str, name: str, modified: str, kind: str, data: bytes) -> None:
        mime = PDF if kind == "pdf" else GOOGLE_DOC
        self.files[fid] = (DriveFile(fid, name, mime, modified), kind, data)

    async def list_files(self, folder_id: str, recursive: bool) -> list[DriveFile]:
        return [f for f, _, _ in self.files.values()]

    async def download(self, file: DriveFile) -> tuple[str, bytes]:
        self.downloads.append(file.id)
        if file.id in self.fail_ids:
            raise ProblemError(502, "drive-error", "boom")
        _, kind, data = self.files[file.id]
        return kind, data


class FakeWeb:
    def __init__(self) -> None:
        self.pages: dict[str, str] = {}
        self.blocked: set[str] = set()
        self.fetched: list[str] = []

    async def allowed(self, url: str) -> bool:
        return url not in self.blocked

    async def fetch(self, url: str) -> FetchedPage:
        self.fetched.append(url)
        if url not in self.pages:
            raise ProblemError(502, "fetch-failed", "Page answered 404")
        return FetchedPage(url, self.pages[url])
