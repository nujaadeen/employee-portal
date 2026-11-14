package com.baracel.portal.gitea.service;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.gitea.domain.*;
import com.baracel.portal.gitea.repo.*;
import com.baracel.portal.task.service.TaskService;
import com.baracel.portal.task.service.TaskService.TaskRef;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class GiteaService {
    private static final Logger log = LoggerFactory.getLogger(GiteaService.class);

    public record GitSummary(String repo, String branch, Integer prNumber, String prState, String checksStatus, int approvals) {}
    public record LinkDto(String repo, String repoUrl, String branch, Integer prNumber, String prTitle, String prState, String prUrl,
                          String checksStatus, int approvals, String lastSha) {}
    public record CommitDto(String sha, String shortSha, String message, String author, Instant committedAt, String url) {}
    public record GitDetail(boolean webhookLive, String baseUrl, List<LinkDto> links, List<CommitDto> commits) {}
    public record RepoDto(Long projectId, String fullName, String defaultBranch, String htmlUrl) {}

    private final TaskService tasks;
    private final GitRepositoryRepository repositories;
    private final TaskGitLinkRepository links;
    private final TaskCommitRepository commits;
    private final PrReviewRequestRepository reviews;
    private final GiteaClient client;
    private final GiteaProperties props;

    public GiteaService(TaskService tasks, GitRepositoryRepository repositories, TaskGitLinkRepository links,
                        TaskCommitRepository commits, PrReviewRequestRepository reviews, GiteaClient client, GiteaProperties props) {
        this.tasks = tasks;
        this.repositories = repositories;
        this.links = links;
        this.commits = commits;
        this.reviews = reviews;
        this.client = client;
        this.props = props;
    }

    // ---- reads for the board, detail and dashboard ----

    /** One summary per task: the link with a pull request wins, otherwise the most recent branch. */
    @Transactional(readOnly = true)
    public Map<Long, GitSummary> summaries(Collection<Long> taskIds) {
        if (taskIds.isEmpty()) return Map.of();
        Map<Long, GitSummary> out = new HashMap<>();
        links.findByTaskIdIn(taskIds).stream()
                .sorted(Comparator.comparing((TaskGitLink l) -> l.prNumber != null).thenComparing(l -> l.updatedAt))
                .forEach(l -> out.put(l.taskId, new GitSummary(l.repo, l.branch, l.prNumber,
                        l.prState == null ? null : l.prState.name(), l.checksStatus == null ? null : l.checksStatus.name(), l.approvals)));
        return out;
    }

    @Transactional(readOnly = true)
    public Instant lastSyncedAt(Collection<Long> taskIds) {
        return taskIds.isEmpty() ? null : links.findByTaskIdIn(taskIds).stream().map(l -> l.updatedAt).max(Comparator.naturalOrder()).orElse(null);
    }

    @Transactional(readOnly = true)
    public GitDetail detail(Long taskId, Long projectId) {
        Map<String, String> urls = repositories.findByProjectId(projectId).stream()
                .filter(r -> r.htmlUrl != null).collect(Collectors.toMap(r -> r.fullName, r -> r.htmlUrl, (a, b) -> a));
        List<LinkDto> l = links.findByTaskIdOrderByUpdatedAtDesc(taskId).stream().map(x -> link(x, urls)).toList();
        List<CommitDto> c = commits.findTop10ByTaskIdOrderByCommittedAtDesc(taskId).stream()
                .map(x -> new CommitDto(x.sha, x.sha.length() > 7 ? x.sha.substring(0, 7) : x.sha, firstLine(x.message), x.authorName, x.committedAt, x.url)).toList();
        return new GitDetail(props.webhookSecret() != null && !props.webhookSecret().isBlank(), props.baseUrl(), l, c);
    }

    @Transactional(readOnly = true)
    public int pendingReviews(String giteaUsername) {
        return giteaUsername == null ? 0
                : reviews.findByReviewerUsernameIgnoreCaseAndStateOrderByUpdatedAtDesc(giteaUsername, "PENDING").size();
    }

    @Transactional
    public List<RepoDto> repositories(CurrentUser me, boolean refresh) {
        Set<Long> projectIds = tasks.memberProjectIds(me);
        List<GitRepository> rows = projectIds.isEmpty() ? List.of() : repositories.findByProjectIdIn(projectIds);
        if (refresh) {
            try {
                Map<String, GiteaClient.RepoInfo> remote = client.listRepositories().stream()
                        .collect(Collectors.toMap(GiteaClient.RepoInfo::fullName, r -> r, (a, b) -> a));
                for (GitRepository r : rows) {
                    GiteaClient.RepoInfo info = remote.get(r.fullName);
                    if (info != null) { r.defaultBranch = info.defaultBranch(); r.htmlUrl = info.htmlUrl(); }
                }
            } catch (RestClientException e) {
                log.warn("Could not refresh repositories from Gitea: {}", e.getMessage());
            }
        }
        return rows.stream().map(r -> new RepoDto(r.projectId, r.fullName, r.defaultBranch, r.htmlUrl))
                .sorted(Comparator.comparing(RepoDto::fullName)).toList();
    }

    // ---- actions ----

    @Transactional
    public LinkDto createBranch(CurrentUser me, String taskKey, String repoName) {
        TaskRef t = tasks.requireMemberRef(me, taskKey);
        GitRepository repo = repository(t.projectId(), repoName);
        String branch = branchName(t.key(), t.title());
        try {
            client.createBranch(repo.fullName, branch, repo.defaultBranch);
        } catch (RestClientException e) {
            log.warn("Gitea branch creation failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "gitea-unavailable", "Gitea could not create the branch: " + e.getMessage());
        }
        TaskGitLink l = link(t.id(), repo.fullName, branch);
        tasks.addActivity(t.id(), me.employeeId(), me.name(), "BRANCH", "created branch " + branch + " in " + repo.fullName);
        return link(l, Map.of());
    }

    @Transactional
    public LinkDto linkPullRequest(CurrentUser me, String taskKey, String repoName, int prNumber) {
        TaskRef t = tasks.requireMemberRef(me, taskKey);
        GitRepository repo = repository(t.projectId(), repoName);
        GiteaClient.PullInfo pr;
        try {
            pr = client.pullRequest(repo.fullName, prNumber);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "gitea-unavailable", "Gitea could not return pull request #" + prNumber + ": " + e.getMessage());
        }
        TaskGitLink l = link(t.id(), repo.fullName, pr.headRef());
        l.prNumber = pr.number();
        l.prTitle = pr.title();
        l.prUrl = pr.htmlUrl();
        l.prState = pr.merged() ? PrState.MERGED : "closed".equals(pr.state()) ? PrState.CLOSED : PrState.OPEN;
        l.lastSha = pr.headSha();
        try {
            String checks = client.checks(repo.fullName, pr.headSha());
            l.checksStatus = checks == null ? null : ChecksStatus.valueOf(checks);
        } catch (RestClientException e) {
            log.debug("No checks for {}: {}", pr.headSha(), e.getMessage());
        }
        tasks.addActivity(t.id(), me.employeeId(), me.name(), "PR", "linked pull request #" + prNumber + " in " + repo.fullName);
        return link(l, Map.of());
    }

    // ---- helpers ----

    /** Only a repository linked to the task's project (project_repository) is accepted. */
    private GitRepository repository(Long projectId, String name) {
        List<GitRepository> mine = repositories.findByProjectId(projectId);
        if (name == null || name.isBlank()) {
            if (mine.size() == 1) return mine.get(0);
            throw ApiException.badRequest("repo-required", "Say which repository to use.");
        }
        return mine.stream().filter(r -> r.fullName.equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("repo-not-linked", "Repository " + name + " is not linked to this project."));
    }

    @Transactional(readOnly = true)
    public List<RepoDto> projectRepositories(CurrentUser me, String projectKey) {
        Long projectId = tasks.memberProjectId(me, projectKey);
        return repositories.findByProjectId(projectId).stream().map(r -> new RepoDto(r.projectId, r.fullName, r.defaultBranch, r.htmlUrl))
                .sorted(Comparator.comparing(RepoDto::fullName)).toList();
    }

    private TaskGitLink link(Long taskId, String repo, String branch) {
        TaskGitLink l = links.findByTaskIdAndRepoAndBranch(taskId, repo, branch).orElseGet(() -> {
            TaskGitLink n = new TaskGitLink();
            n.taskId = taskId;
            n.repo = repo;
            n.branch = branch;
            return links.save(n);
        });
        l.updatedAt = Instant.now();
        return l;
    }

    private LinkDto link(TaskGitLink l, Map<String, String> urls) {
        return new LinkDto(l.repo, urls.get(l.repo), l.branch, l.prNumber, l.prTitle, l.prState == null ? null : l.prState.name(), l.prUrl,
                l.checksStatus == null ? null : l.checksStatus.name(), l.approvals, l.lastSha);
    }

    /** feature/bar-142-whatsapp-otp-delivery */
    public static String branchName(String key, String title) {
        String slug = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("-+$", "");
        return "feature/" + key.toLowerCase() + (slug.isEmpty() ? "" : "-" + slug);
    }

    private static String firstLine(String s) { int i = s.indexOf('\n'); return i < 0 ? s : s.substring(0, i); }
}
