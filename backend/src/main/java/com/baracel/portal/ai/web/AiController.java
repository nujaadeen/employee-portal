package com.baracel.portal.ai.web;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.ai.service.AiChatStreamService;
import com.baracel.portal.ai.service.AiService;
import com.baracel.portal.ai.service.AiService.*;
import com.baracel.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
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
    public record NewConversation(@Nullable String title) {}
    public record SendMessage(@NotBlank String message) {}
    public record PublishRequest(@NotEmpty List<String> channels) {}
    public record FeedbackRequest(@NotBlank String rating, @Nullable String comment) {}
    public record DraftPatch(@Nullable String headline, @Nullable String caption, @Nullable List<String> hashtags) {}

    private final AiService service;
    private final AiChatStreamService streams;

    public AiController(AiService service, AiChatStreamService streams) {
        this.service = service;
        this.streams = streams;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> list(CurrentUser me) { return service.list(me); }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> create(CurrentUser me, @RequestBody(required = false) NewConversation body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me, body == null ? null : body.title()));
    }

    @GetMapping("/conversations/{id}")
    public ConversationDetail get(CurrentUser me, @PathVariable UUID id) { return service.get(me, id); }

    /**
     * Send a message. With {@code Accept: text/event-stream} the answer is streamed as server-sent events
     * (token, sources, draft, done, error); without it the endpoint returns plain JSON.
     */
    @PostMapping("/conversations/{id}/messages")
    @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = Exchange.class)),
            @Content(mediaType = "text/event-stream", schema = @Schema(type = "string"))})
    public Object send(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody SendMessage body,
                       @RequestHeader(value = "Accept", required = false) String accept) {
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) return streams.stream(me, id, body.message());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.send(me, id, body.message()));
    }

    @PostMapping("/messages/{id}/feedback")
    public FeedbackDto feedback(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody FeedbackRequest body) {
        return service.feedback(me, id, body.rating(), body.comment());
    }

    @PatchMapping("/drafts/{id}")
    public DraftDto editDraft(CurrentUser me, @PathVariable UUID id, @RequestBody DraftPatch body) {
        return service.updateDraft(me, id, body.headline(), body.caption(), body.hashtags());
    }

    @GetMapping("/access")
    public AiAccess access(CurrentUser me) { return service.access(me); }

    @PostMapping("/drafts/{id}/publish")
    public List<PublishResult> publish(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody PublishRequest body) {
        return service.publish(me, id, body.channels());
    }
}
