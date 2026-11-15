package com.baracel.portal.ai.client;

import com.baracel.portal.ai.service.AiAssistantPort.AiDraftReply;
import com.baracel.portal.ai.service.AiAssistantPort.AiSource;
import com.baracel.portal.common.web.ApiException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

/**
 * Everything Spring says to the AI service. Calls carry a service token (ai:invoke for chat, ai:ingest for knowledge
 * management), have timeouts, and go through a circuit breaker. A failure or an open breaker becomes 503 ai-unavailable.
 * Chat calls are never retried automatically.
 */
public class AiServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE = new ParameterizedTypeReference<>() {};

    private final AiProperties.Service cfg;
    private final AiServiceTokens tokens;
    private final SimpleCircuitBreaker breaker;
    private final RestClient chatHttp;
    private final RestClient adminHttp;
    private final WebClient streamHttp;

    public AiServiceClient(AiProperties props, AiServiceTokens tokens, SimpleCircuitBreaker breaker) {
        this.cfg = props.service();
        this.tokens = tokens;
        this.breaker = breaker;
        this.chatHttp = restClient(cfg.chatTimeout());
        this.adminHttp = restClient(cfg.adminTimeout());
        this.streamHttp = WebClient.builder().baseUrl(cfg.url())
                .clientConnector(new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create()
                        .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) cfg.connectTimeout().toMillis())))
                .build();
    }

    private RestClient restClient(java.time.Duration readTimeout) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(cfg.connectTimeout()).build());
        f.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(cfg.url()).requestFactory(f).build();
    }

    public SimpleCircuitBreaker breaker() { return breaker; }

    // ---- chat ----

    public JsonNode chat(Map<String, Object> body) {
        return call(AiServiceTokens.INVOKE, token -> chatHttp.post().uri("/v1/chat").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
    }

    /** The AI service's SSE stream. The breaker must have been checked with {@link #acquire()} by the caller, which also reports the outcome. */
    public Flux<ServerSentEvent<String>> stream(Map<String, Object> body) {
        String token;
        try {
            token = tokens.token(AiServiceTokens.INVOKE);
        } catch (RuntimeException e) {
            return Flux.error(e);
        }
        return streamHttp.post().uri("/v1/chat").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM).bodyValue(body).retrieve().bodyToFlux(SSE).timeout(cfg.streamIdleTimeout());
    }

    /** Throws 503 ai-unavailable when the breaker is open. */
    public void acquire() {
        if (!breaker.tryAcquire()) throw ApiException.unavailable("ai-unavailable", "The AI service is not available right now. Try again in a minute.");
    }

    public static boolean isUnavailable(Throwable t) {
        return t instanceof WebClientRequestException || t instanceof java.util.concurrent.TimeoutException
                || t instanceof java.net.ConnectException || t instanceof io.netty.handler.timeout.ReadTimeoutException
                || (t instanceof WebClientResponseException w && w.getStatusCode().is5xxServerError())
                || (t instanceof WebClientResponseException w2 && (w2.getStatusCode().value() == 401 || w2.getStatusCode().value() == 403))
                || t instanceof org.springframework.security.oauth2.core.OAuth2AuthorizationException
                || t instanceof IllegalStateException;
    }

    // ---- knowledge management (scope ai:ingest) ----

    public JsonNode collections() { return admin("GET", "/v1/collections"); }
    public JsonNode documents(String code) { return admin("GET", "/v1/collections/" + code + "/documents"); }
    public JsonNode job(String id) { return admin("GET", "/v1/jobs/" + id); }
    public void deleteDocument(String code, String id) { admin("DELETE", "/v1/collections/" + code + "/documents/" + id); }
    public JsonNode drive(String code, Map<String, Object> body) { return adminPost("/v1/collections/" + code + "/sources/drive", body); }
    public JsonNode web(String code, Map<String, Object> body) { return adminPost("/v1/collections/" + code + "/sources/web", body); }
    public JsonNode reindex(String code) { return adminPost("/v1/collections/" + code + "/reindex", null); }

    /** Streams the upload through: the file is read from the (disk-backed) multipart part, never loaded into memory. */
    public JsonNode uploadPdf(String code, String filename, String contentType, InputStream in, long size) {
        Resource part = new InputStreamResource(in) {
            @Override public String getFilename() { return filename; }
            @Override public long contentLength() { return size; }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(MediaType.parseMediaType(contentType));
        form.add("file", new org.springframework.http.HttpEntity<>(part, h));
        return call(AiServiceTokens.INGEST, token -> adminHttp.post().uri("/v1/collections/" + code + "/documents")
                .header("Authorization", "Bearer " + token).contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class));
    }

    private JsonNode admin(String method, String path) {
        return call(AiServiceTokens.INGEST, token -> {
            RestClient.RequestHeadersSpec<?> spec = switch (method) {
                case "DELETE" -> adminHttp.delete().uri(path);
                default -> adminHttp.get().uri(path);
            };
            return spec.header("Authorization", "Bearer " + token).retrieve().body(JsonNode.class);
        });
    }

    private JsonNode adminPost(String path, Map<String, Object> body) {
        return call(AiServiceTokens.INGEST, token -> {
            RestClient.RequestBodySpec spec = adminHttp.post().uri(path).header("Authorization", "Bearer " + token);
            if (body != null) spec.contentType(MediaType.APPLICATION_JSON).body(body);
            return spec.retrieve().body(JsonNode.class);
        });
    }

    // ---- plumbing ----

    private <T> T call(String scope, java.util.function.Function<String, T> fn) {
        acquire();
        try {
            T result = fn.apply(tokens.token(scope));
            breaker.success();
            return result;
        } catch (HttpClientErrorException e) {
            breaker.success();   // the service answered
            throw mapClientError(e);
        } catch (HttpServerErrorException | ResourceAccessException | IllegalStateException
                 | org.springframework.security.oauth2.core.OAuth2AuthorizationException e) {
            breaker.failure();
            log.warn("AI service call failed: {}", e.getClass().getSimpleName());
            throw ApiException.unavailable("ai-unavailable", "The AI service is not available right now. Try again in a minute.");
        }
    }

    private ApiException mapClientError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            log.error("The AI service refused our service token ({}). Check the portal-backend-ai client and its scopes.", status);
            breaker.failure();
            return ApiException.unavailable("ai-unavailable", "The AI service is not available right now. Try again in a minute.");
        }
        String type = "ai-service-error";
        String detail = "The AI service refused the request.";
        try {
            JsonNode n = e.getResponseBodyAs(JsonNode.class);
            if (n != null) {
                String t = n.path("type").asString("");
                if (!t.isEmpty()) type = t.substring(t.lastIndexOf('/') + 1);
                String d = n.path("detail").asString("");
                if (!d.isEmpty()) detail = d;
            }
        } catch (RuntimeException ignored) { /* keep the defaults */ }
        return new ApiException(org.springframework.http.HttpStatus.valueOf(status), type, detail);
    }

    // ---- JSON mapping shared by the port implementation ----

    public static List<AiSource> sources(JsonNode arr) {
        List<AiSource> out = new ArrayList<>();
        if (arr != null && arr.isArray()) for (JsonNode s : arr)
            out.add(new AiSource(s.path("title").asString(""), s.path("collection").asString(""),
                    s.hasNonNull("page") ? s.get("page").asInt() : null, s.path("source_ref").asString(null)));
        return out;
    }

    public static AiDraftReply draft(JsonNode d) {
        if (d == null || d.isNull() || d.isMissingNode()) return null;
        List<String> tags = new ArrayList<>();
        d.path("hashtags").forEach(t -> tags.add(t.asString("")));
        return new AiDraftReply("SOCIAL_POST", d.path("headline").asString(""), d.path("caption").asString(""), tags, List.of(),
                d.path("platform").asString("LINKEDIN"), d.path("language").asString("en"));
    }

}
