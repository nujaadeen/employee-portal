package com.baracel.portal.gitea.web;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.gitea.service.GiteaService;
import com.baracel.portal.gitea.service.GiteaService.LinkDto;
import com.baracel.portal.gitea.service.GiteaService.RepoDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Gitea")
public class GiteaController {
    public record BranchRequest(@Nullable String repo) {}
    public record LinkPrRequest(@Nullable String repo, @NotNull Integer prNumber) {}

    private final GiteaService service;

    public GiteaController(GiteaService service) { this.service = service; }

    @PostMapping("/api/tasks/{key}/git/branch")
    @PreAuthorize("hasAuthority('task:edit')")
    public LinkDto createBranch(CurrentUser me, @PathVariable String key, @RequestBody(required = false) BranchRequest body) {
        return service.createBranch(me, key, body == null ? null : body.repo());
    }

    @PostMapping("/api/tasks/{key}/git/link-pr")
    @PreAuthorize("hasAuthority('task:edit')")
    public LinkDto linkPr(CurrentUser me, @PathVariable String key, @Valid @RequestBody LinkPrRequest body) {
        return service.linkPullRequest(me, key, body.repo(), body.prNumber());
    }

    @GetMapping("/api/projects/{key}/repos")
    @PreAuthorize("hasAuthority('task:view')")
    public List<RepoDto> projectRepos(CurrentUser me, @PathVariable String key) { return service.projectRepositories(me, key); }

    @GetMapping("/api/repos")
    @PreAuthorize("hasAuthority('repo:view')")
    public List<RepoDto> repos(CurrentUser me, @RequestParam(defaultValue = "false") boolean refresh) {
        return service.repositories(me, refresh);
    }
}
