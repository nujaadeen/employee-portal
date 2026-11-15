import httpx

from tests.conftest import _OTHER_KEY, ISSUER, auth, chat_body, make_token


async def test_health_endpoints_are_open(client: httpx.AsyncClient) -> None:
    assert (await client.get("/healthz")).json() == {"status": "ok"}
    r = await client.get("/readyz")
    assert r.status_code == 200 and r.json()["checks"] == {"database": True, "chroma": True, "llm": True}


async def test_401_without_a_token_and_with_bad_ones(client: httpx.AsyncClient) -> None:
    body = chat_body("hello", ["product"])
    r = await client.post("/v1/chat", json=body)
    assert r.status_code == 401 and r.headers["content-type"].startswith("application/problem+json")
    assert r.headers["www-authenticate"] == "Bearer"
    bad = {
        "garbage": "not-a-jwt",
        "wrong audience": make_token(aud="portal-api"),
        "expired": make_token(exp_in=-3600),
        "wrong issuer": make_token(issuer="http://evil.example/realms/x"),
        "wrong signature": make_token(key=_OTHER_KEY),
    }
    for name, token in bad.items():
        r = await client.post("/v1/chat", json=body, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 401, name
    assert ISSUER  # the issuer used above is the configured one


async def test_403_with_the_wrong_scope(client: httpx.AsyncClient) -> None:
    r = await client.post("/v1/chat", json=chat_body("hi", ["product"]), headers=auth("ai:ingest"))
    assert r.status_code == 403 and r.json()["type"].endswith("/insufficient-scope")
    for method, url in [
        ("get", "/v1/collections"),
        ("get", "/v1/jobs/x"),
        ("post", "/v1/collections/product/reindex"),
        ("get", "/v1/collections/product/documents"),
        ("delete", "/v1/collections/product/documents/x"),
    ]:
        r = await client.request(method, url, headers=auth("ai:invoke"))
        assert r.status_code == 403, url
    r = await client.post(
        "/v1/collections/product/documents", headers=auth("ai:invoke"), files={"file": ("a.pdf", b"%PDF-1", "application/pdf")}
    )
    assert r.status_code == 403


async def test_request_id_is_returned_and_validation_does_not_echo_input(client: httpx.AsyncClient) -> None:
    r = await client.post(
        "/v1/chat", json={"message": "secret text", "user": 5}, headers={**auth(), "X-Request-Id": "abc-12345678"}
    )
    assert r.status_code == 422 and r.headers["x-request-id"] == "abc-12345678"
    assert "secret text" not in r.text
