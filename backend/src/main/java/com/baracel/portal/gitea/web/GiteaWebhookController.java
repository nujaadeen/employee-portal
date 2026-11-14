package com.baracel.portal.gitea.web;

import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.gitea.service.GiteaProperties;
import com.baracel.portal.gitea.service.GiteaWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Public endpoint (no JWT). It is protected by the HMAC-SHA256 signature of the raw body. */
@RestController
@RequestMapping("/api/integrations/gitea")
@Tag(name = "Gitea")
public class GiteaWebhookController {
    public record WebhookResult(String event, int tasksUpdated) {}

    private final GiteaProperties props;
    private final GiteaWebhookService service;
    private final ObjectMapper mapper;

    public GiteaWebhookController(GiteaProperties props, GiteaWebhookService service, ObjectMapper mapper) {
        this.props = props;
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/webhook", consumes = "*/*")
    public WebhookResult webhook(@RequestBody byte[] body,
                                 @RequestHeader(value = "X-Gitea-Signature", required = false) String signature,
                                 @RequestHeader(value = "X-Gitea-Event", required = false) String event) {
        if (!validSignature(body, signature))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-signature", "The webhook signature is missing or wrong.");
        JsonNode payload = mapper.readTree(body);
        return new WebhookResult(event, service.handle(event, payload));
    }

    boolean validSignature(byte[] body, String signature) {
        String secret = props.webhookSecret();
        if (secret == null || secret.isBlank() || signature == null) return false;   // fail closed
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }
}
