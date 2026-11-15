"""Web source: same domain only, robots.txt obeyed, page limit, no private addresses unless allowed."""

import asyncio
import ipaddress
import re
import socket
from dataclasses import dataclass
from typing import Protocol
from urllib import robotparser
from urllib.parse import urldefrag, urljoin, urlparse

import httpx

from ai_service.core.errors import ProblemError


@dataclass(frozen=True)
class FetchedPage:
    url: str
    html: str


class WebFetcher(Protocol):
    async def allowed(self, url: str) -> bool: ...
    async def fetch(self, url: str) -> FetchedPage: ...


def validate_urls(urls: list[str]) -> list[str]:
    """Same domain only: every URL must be http(s) and share the host of the first one."""
    if not urls:
        raise ProblemError(422, "no-urls", "Give at least one URL")
    host: str | None = None
    out: list[str] = []
    for raw in urls:
        u = urlparse(raw)
        if u.scheme not in ("http", "https") or not u.hostname:
            raise ProblemError(422, "invalid-url", "Only http and https URLs are accepted")
        host = host or u.hostname.lower()
        if u.hostname.lower() != host:
            raise ProblemError(422, "different-domain", "All URLs must be on the same domain")
        out.append(urldefrag(raw)[0])
    return list(dict.fromkeys(out))


def _is_private(host: str) -> bool:
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror:
        return False
    for info in infos:
        ip = ipaddress.ip_address(info[4][0])
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved:
            return True
    return False


class HttpWebFetcher:
    def __init__(self, user_agent: str, allow_private: bool) -> None:
        self._ua = user_agent
        self._allow_private = allow_private
        self._robots: dict[str, robotparser.RobotFileParser] = {}

    async def _guard(self, url: str) -> None:
        host = urlparse(url).hostname or ""
        if not self._allow_private and await asyncio.to_thread(_is_private, host):
            raise ProblemError(422, "private-address", "Private network addresses cannot be crawled")

    async def allowed(self, url: str) -> bool:
        u = urlparse(url)
        origin = f"{u.scheme}://{u.netloc}"
        rp = self._robots.get(origin)
        if rp is None:
            rp = robotparser.RobotFileParser()
            try:
                async with httpx.AsyncClient(timeout=10, headers={"User-Agent": self._ua}) as c:
                    r = await c.get(f"{origin}/robots.txt", follow_redirects=True)
                if r.status_code == 200:
                    rp.parse(r.text.splitlines())
                else:
                    rp.parse([])  # no robots.txt: everything is allowed
            except httpx.HTTPError:
                rp.parse(["User-agent: *", "Disallow: /"])  # cannot read it: be careful
            self._robots[origin] = rp
        return rp.can_fetch(self._ua, url)

    async def fetch(self, url: str) -> FetchedPage:
        await self._guard(url)
        async with httpx.AsyncClient(timeout=20, headers={"User-Agent": self._ua}, follow_redirects=False) as c:
            r = await c.get(url)
        if r.status_code != 200 or "html" not in r.headers.get("content-type", ""):
            raise ProblemError(502, "fetch-failed", f"Page answered {r.status_code}")
        return FetchedPage(url, r.text)


_HREF = re.compile(r'href=["\']([^"\'#][^"\']*)["\']', re.IGNORECASE)


def same_domain_links(page: FetchedPage) -> list[str]:
    host = urlparse(page.url).hostname
    links: list[str] = []
    for href in _HREF.findall(page.html):
        absolute = urldefrag(urljoin(page.url, href))[0]
        u = urlparse(absolute)
        if (
            u.scheme in ("http", "https")
            and u.hostname == host
            and not re.search(r"\.(png|jpe?g|gif|svg|css|js|zip|pdf)$", u.path, re.I)
        ):
            links.append(absolute)
    return list(dict.fromkeys(links))
