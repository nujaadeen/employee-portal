package com.baracel.portal.ai.web;

import com.baracel.portal.ai.service.AiService;
import com.baracel.portal.ai.service.AiService.*;
import com.baracel.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAuthority('ai:chat')")
@Tag(name = "AI assistant")
public class AiController {
    public record NewConversation(String title) {}
    public record SendMessage(@NotBlank String message) {}
    public record PublishRequest(@NotEmpty List<String> channels) {}

    private final AiService service;

    public AiController(AiService service) { this.service = service; }

    @GetMapping("/conversations")
    public List<ConversationDto> list(CurrentUser me) { return service.list(me); }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> create(CurrentUser me, @RequestBody(required = false) NewConversation body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me, body == null ? null : body.title()));
    }

    @GetMapping("/conversations/{id}")
    public ConversationDetail get(CurrentUser me, @PathVariable UUID id) { return service.get(me, id); }

    @PostMapping("/conversations/{id}/messages")
    public Exchange send(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody SendMessage body) {
        return service.send(me, id, body.message());
    }

    @GetMapping("/access")
    public AiAccess access(CurrentUser me) { return service.access(me); }

    @PostMapping("/drafts/{id}/publish")
    public List<PublishResult> publish(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody PublishRequest body) {
        return service.publish(me, id, body.channels());
    }
}
