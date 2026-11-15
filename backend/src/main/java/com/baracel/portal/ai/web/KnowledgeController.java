package com.baracel.portal.ai.web;

import com.baracel.portal.ai.service.KnowledgeService;
import com.baracel.portal.ai.service.KnowledgeService.*;
import com.baracel.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Knowledge base administration (ADMIN, permission ai:knowledge:manage). Document work is proxied to the AI service. */
@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasAuthority('ai:knowledge:manage')")
@Tag(name = "Knowledge base")
public class KnowledgeController {
    public record NewCollection(@NotBlank String code, @NotBlank String name) {}
    public record RolesBody(@NotEmpty List<String> roles) {}
    public record RolesResult(String code, List<String> roles) {}

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) { this.service = service; }

    @GetMapping("/collections")
    public KnowledgeOverview collections() { return service.overview(); }

    @PostMapping("/collections")
    public ResponseEntity<CollectionAdmin> create(CurrentUser me, @Valid @RequestBody NewCollection body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me, body.code(), body.name()));
    }

    @PutMapping("/collections/{code}/roles")
    public RolesResult roles(CurrentUser me, @PathVariable String code, @Valid @RequestBody RolesBody body) {
        return new RolesResult(code, service.setRoles(me, code, body.roles()));
    }

    @DeleteMapping("/collections/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser me, @PathVariable String code) { service.delete(me, code); }

    @PostMapping(value = "/collections/{code}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted upload(CurrentUser me, @PathVariable String code, @RequestPart("file") MultipartFile file) { return service.upload(me, code, file); }

    @PostMapping("/collections/{code}/sources/drive")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted drive(CurrentUser me, @PathVariable String code, @Valid @RequestBody DriveSourceBody body) {
        return service.drive(me, code, new DriveSource(body.folderId(), body.recursive(), body.force()));
    }
    public record DriveSourceBody(@NotBlank String folderId, @com.baracel.portal.common.web.Nullable Boolean recursive, @com.baracel.portal.common.web.Nullable Boolean force) {}

    @PostMapping("/collections/{code}/sources/web")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted web(CurrentUser me, @PathVariable String code, @Valid @RequestBody WebSourceBody body) {
        return service.web(me, code, new WebSource(body.urls(), body.followLinks(), body.maxPages(), body.force()));
    }
    public record WebSourceBody(@NotEmpty List<String> urls, @com.baracel.portal.common.web.Nullable Boolean followLinks,
                                @com.baracel.portal.common.web.Nullable Integer maxPages, @com.baracel.portal.common.web.Nullable Boolean force) {}

    @PostMapping("/collections/{code}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted reindex(CurrentUser me, @PathVariable String code) { return service.reindex(me, code); }

    @GetMapping("/collections/{code}/documents")
    public List<KnowledgeDocument> documents(@PathVariable String code) { return service.documents(code); }

    @DeleteMapping("/collections/{code}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(CurrentUser me, @PathVariable String code, @PathVariable String documentId) { service.deleteDocument(me, code, documentId); }

    @GetMapping("/jobs/{id}")
    public KnowledgeJob job(@PathVariable String id) { return service.job(id); }

    @GetMapping("/activity")
    public List<ActivityItem> activity() { return service.recentActivity(); }
}
