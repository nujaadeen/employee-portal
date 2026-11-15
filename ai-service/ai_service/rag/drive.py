"""Google Drive source. A service account key (GOOGLE_SA_KEY_PATH) read-only access to a shared folder."""

import asyncio
import io
from dataclasses import dataclass
from typing import Any, Protocol

from ai_service.core.errors import ProblemError

GOOGLE_DOC = "application/vnd.google-apps.document"
GOOGLE_FOLDER = "application/vnd.google-apps.folder"
PDF = "application/pdf"


@dataclass(frozen=True)
class DriveFile:
    id: str
    name: str
    mime_type: str
    modified_time: str


class DriveClient(Protocol):
    async def list_files(self, folder_id: str, recursive: bool) -> list[DriveFile]: ...
    async def download(self, file: DriveFile) -> tuple[str, bytes]:
        """Returns ('pdf', bytes) or ('text', utf-8 bytes)."""
        ...


class GoogleDriveClient:
    def __init__(self, key_path: str) -> None:
        self._key_path = key_path
        self._service: Any = None

    def _svc(self) -> Any:
        if self._service is None:
            from google.oauth2 import service_account
            from googleapiclient.discovery import build

            creds = service_account.Credentials.from_service_account_file(  # type: ignore[no-untyped-call]
                self._key_path, scopes=["https://www.googleapis.com/auth/drive.readonly"]
            )
            self._service = build("drive", "v3", credentials=creds, cache_discovery=False)
        return self._service

    def _list(self, folder_id: str, recursive: bool) -> list[DriveFile]:
        svc, out, stack = self._svc(), [], [folder_id]
        while stack:
            current = stack.pop()
            token = None
            while True:
                res = (
                    svc.files()
                    .list(
                        q=f"'{current}' in parents and trashed = false",
                        fields="nextPageToken, files(id, name, mimeType, modifiedTime)",
                        pageSize=200,
                        pageToken=token,
                        supportsAllDrives=True,
                        includeItemsFromAllDrives=True,
                    )
                    .execute()
                )
                for f in res.get("files", []):
                    if f["mimeType"] == GOOGLE_FOLDER:
                        if recursive:
                            stack.append(f["id"])
                    elif f["mimeType"] in (PDF, GOOGLE_DOC):
                        out.append(DriveFile(f["id"], f["name"], f["mimeType"], f.get("modifiedTime", "")))
                token = res.get("nextPageToken")
                if not token:
                    break
        return out

    def _download(self, file: DriveFile) -> tuple[str, bytes]:
        from googleapiclient.http import MediaIoBaseDownload

        svc = self._svc()
        mime = file.mime_type or svc.files().get(fileId=file.id, fields="mimeType", supportsAllDrives=True).execute()["mimeType"]
        if mime == GOOGLE_DOC:
            data = svc.files().export(fileId=file.id, mimeType="text/plain").execute()
            return "text", data if isinstance(data, bytes) else str(data).encode()
        buf = io.BytesIO()
        downloader = MediaIoBaseDownload(buf, svc.files().get_media(fileId=file.id, supportsAllDrives=True))
        done = False
        while not done:
            _, done = downloader.next_chunk()
        return "pdf", buf.getvalue()

    async def list_files(self, folder_id: str, recursive: bool) -> list[DriveFile]:
        return await asyncio.to_thread(self._list, folder_id, recursive)

    async def download(self, file: DriveFile) -> tuple[str, bytes]:
        return await asyncio.to_thread(self._download, file)


class UnconfiguredDrive:
    async def list_files(self, folder_id: str, recursive: bool) -> list[DriveFile]:
        raise ProblemError(503, "drive-not-configured", "Google Drive is not configured (set GOOGLE_SA_KEY_PATH)")

    async def download(self, file: DriveFile) -> tuple[str, bytes]:
        raise ProblemError(503, "drive-not-configured", "Google Drive is not configured (set GOOGLE_SA_KEY_PATH)")


def build_drive(key_path: str) -> DriveClient:
    return GoogleDriveClient(key_path) if key_path else UnconfiguredDrive()
