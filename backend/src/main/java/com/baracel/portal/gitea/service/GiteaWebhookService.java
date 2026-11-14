package com.baracel.portal.gitea.service;

import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.gitea.domain.*;
import com.baracel.portal.gitea.repo.*;
import com.baracel.portal.task.domain.TaskStatus;
import com.baracel.portal.task.service.TaskService;
import com.baracel.portal.task.service.TaskService.TaskRef;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Turns Gitea webhook payloads into git links, commits, task activity and (configurable) status moves. */
@Service
public class GiteaWebhookService {
    private static final Logger log = LoggerFactory.getLogger(GiteaWebhookService.class);
    /** Task keys such as BAR-142. Branch names are lower case, so the match ignores case. */
    static final Pattern TASK_KEY = Pattern.compile("(?i)(?<![A-Za-z0-9])([A-Z]{2,6})-(\\d+)(?![A-Za-z0-9])");

    private final TaskService tasks;
    private final EmployeeService employees;
    private final TaskGitLinkRepository links;
    private final TaskCommitRepository commits;
    private final PrReviewRequestRepository reviews;
    private final GiteaProperties props;

    public GiteaWebhookService(TaskService tasks, EmployeeService employees, TaskGitLinkRepository links, TaskCommitRepository commits,
                               PrReviewRequestRepository reviews, GiteaProperties props) {
        this.tasks = tasks;
        this.employees = employees;
        this.links = links;
        this.commits = commits;
        this.reviews = reviews;
        this.props = props;
    }

    public static Set<String> keys(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : texts) {
            if (t == null) continue;
            Matcher m = TASK_KEY.matcher(t);
            while (m.find()) out.add((m.group(1) + "-" + m.group(2)).toUpperCase());
        }
        return out;
    }

    public static String mapChecks(String giteaState) {
        return switch (giteaState.toLowerCase()) {
            case "success" -> "PASSING";
            case "failure", "error" -> "FAILING";
            default -> "PENDING";
        };
    }

    /** Returns the number of tasks that were touched. */
    @Transactional
    public int handle(String event, JsonNode p) {
        String e = event == null ? "" : event.toLowerCase();
        return switch (e) {
            case "push" -> push(p);
            case "create" -> create(p);
            case "pull_request" -> pullRequest(p);
            case "status" -> status(p);
            default -> e.startsWith("pull_request_") || e.equals("pull_request_review") ? review(p) : 0;
        };
    }

    // ---- push and create ----

    private int push(JsonNode p) {
        String repo = p.path("repository").path("full_name").asText();
        String ref = p.path("ref").asText();
        String branch = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        Set<String> branchKeys = keys(branch);
        JsonNode commitNodes = p.path("commits");
        Set<String> all = new LinkedHashSet<>(branchKeys);
        for (JsonNode c : commitNodes) all.addAll(keys(c.path("message").asText()));
        Map<String, TaskRef> found = tasks.existing(all);
        String actor = actor(p);
        String head = p.path("after").asText(commitNodes.isEmpty() ? null : commitNodes.get(commitNodes.size() - 1).path("id").asText());
        for (String key : found.keySet()) {
            TaskRef t = found.get(key);
            int added = 0;
            for (JsonNode c : commitNodes) {
                if (!branchKeys.contains(key) && !keys(c.path("message").asText()).contains(key)) continue;
                String sha = c.path("id").asText();
                if (commits.existsByTaskIdAndRepoAndSha(t.id(), repo, sha)) continue;
                TaskCommit tc = new TaskCommit();
                tc.taskId = t.id();
                tc.repo = repo;
                tc.sha = sha;
                tc.message = c.path("message").asText().strip();
                tc.authorName = c.path("author").path("name").asText(c.path("author").path("username").asText(actor));
                tc.committedAt = time(c.path("timestamp").asText(null));
                tc.url = c.path("url").asText(null);
                commits.save(tc);
                added++;
            }
            if (branchKeys.contains(key)) {
                TaskGitLink l = link(t.id(), repo, branch);
                if (head != null && !head.isBlank()) l.lastSha = head;
            }
            if (added > 0)
                tasks.addActivity(t.id(), actor, "COMMIT", "pushed " + added + " commit" + (added > 1 ? "s" : "") + " to " + branch + " in " + repo);
        }
        return found.size();
    }

    private int create(JsonNode p) {
        if (!"branch".equals(p.path("ref_type").asText())) return 0;
        String repo = p.path("repository").path("full_name").asText();
        String branch = p.path("ref").asText();
        Map<String, TaskRef> found = tasks.existing(keys(branch));
        for (TaskRef t : found.values()) {
            TaskGitLink l = link(t.id(), repo, branch);
            String sha = p.path("sha").asText(null);
            if (sha != null && l.lastSha == null) l.lastSha = sha;
            tasks.addActivity(t.id(), actor(p), "BRANCH", "created branch " + branch + " in " + repo);
        }
        return found.size();
    }

    // ---- pull requests and reviews ----

    private int pullRequest(JsonNode p) {
        String action = p.path("action").asText();
        JsonNode pr = p.path("pull_request");
        String repo = p.path("repository").path("full_name").asText();
        int number = pr.path("number").asInt(p.path("number").asInt());
        String title = pr.path("title").asText();
        String actor = actor(p);

        if ("review_requested".equals(action)) {
            String reviewer = p.path("requested_reviewer").path("login").asText(null);
            if (reviewer != null) review(repo, number, title, pr.path("html_url").asText(null), reviewer, "PENDING");
        } else if ("review_request_removed".equals(action)) {
            String reviewer = p.path("requested_reviewer").path("login").asText(null);
            if (reviewer != null) review(repo, number, title, pr.path("html_url").asText(null), reviewer, "DONE");
        }

        boolean merged = pr.path("merged").asBoolean(false);
        boolean closed = "closed".equals(pr.path("state").asText()) || "closed".equals(action);
        if (closed) reviews.findByRepoAndPrNumber(repo, number).forEach(r -> { r.state = "DONE"; r.updatedAt = Instant.now(); });

        String headRef = pr.path("head").path("ref").asText();
        Map<String, TaskRef> found = tasks.existing(keys(headRef, title, pr.path("body").asText(null)));
        for (TaskRef t : found.values()) {
            TaskGitLink l = link(t.id(), repo, headRef);
            l.prNumber = number;
            l.prTitle = title;
            l.prUrl = pr.path("html_url").asText(l.prUrl);
            l.prState = merged ? PrState.MERGED : closed ? PrState.CLOSED : PrState.OPEN;
            String sha = pr.path("head").path("sha").asText(null);
            if (sha != null && !sha.isBlank()) {
                if (!sha.equals(l.lastSha)) l.checksStatus = null;
                l.lastSha = sha;
            }
            switch (action) {
                case "opened" -> {
                    tasks.addActivity(t.id(), actor, "PR", "opened pull request #" + number + " in " + repo);
                    if (props.moveToReviewOnPrOpen()) tasks.moveIf(t.id(), TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, actor, "pull request opened");
                }
                case "reopened" -> tasks.addActivity(t.id(), actor, "PR", "reopened pull request #" + number + " in " + repo);
                case "closed" -> {
                    tasks.addActivity(t.id(), actor, "PR", (merged ? "merged" : "closed") + " pull request #" + number + " in " + repo);
                    if (merged && props.moveToDoneOnPrMerge()) tasks.moveIf(t.id(), TaskStatus.IN_REVIEW, TaskStatus.DONE, actor, "pull request merged");
                }
                default -> { }
            }
        }
        return found.size();
    }

    private int review(JsonNode p) {
        JsonNode pr = p.path("pull_request");
        String repo = p.path("repository").path("full_name").asText();
        int number = pr.path("number").asInt();
        String type = p.path("review").path("type").asText(p.path("action").asText());
        String reviewer = p.path("sender").path("login").asText(null);
        if (reviewer != null) review(repo, number, pr.path("title").asText(), pr.path("html_url").asText(null), reviewer, "DONE");
        String headRef = pr.path("head").path("ref").asText();
        Map<String, TaskRef> found = tasks.existing(keys(headRef, pr.path("title").asText(), pr.path("body").asText(null)));
        String actor = actor(p);
        for (TaskRef t : found.values()) {
            TaskGitLink l = link(t.id(), repo, headRef);
            if (l.prNumber == null) { l.prNumber = number; l.prTitle = pr.path("title").asText(); l.prState = PrState.OPEN; }
            String verb;
            if (type.contains("approved")) { l.approvals++; verb = "approved"; }
            else if (type.contains("rejected")) verb = "requested changes on";
            else verb = "reviewed";
            tasks.addActivity(t.id(), actor, "REVIEW", verb + " pull request #" + number + " in " + repo);
        }
        return found.size();
    }

    private void review(String repo, int number, String title, String url, String reviewer, String state) {
        PrReviewRequest r = reviews.findByRepoAndPrNumberAndReviewerUsernameIgnoreCase(repo, number, reviewer).orElseGet(() -> {
            PrReviewRequest n = new PrReviewRequest();
            n.repo = repo;
            n.prNumber = number;
            n.reviewerUsername = reviewer;
            return reviews.save(n);
        });
        r.prTitle = title;
        r.prUrl = url;
        r.state = state;
        r.updatedAt = Instant.now();
    }

    // ---- commit status (checks) ----

    private int status(JsonNode p) {
        String repo = p.path("repository").path("full_name").asText();
        String sha = p.path("sha").asText();
        ChecksStatus cs = ChecksStatus.valueOf(mapChecks(p.path("state").asText()));
        List<TaskGitLink> hit = links.findByRepoAndLastSha(repo, sha);
        hit.forEach(l -> { l.checksStatus = cs; l.updatedAt = Instant.now(); });
        return hit.size();
    }

    // ---- helpers ----

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

    private String actor(JsonNode p) {
        String login = p.path("sender").path("login").asText(p.path("pusher").path("login").asText(null));
        if (login == null) return "Gitea";
        return employees.findByGiteaUsername(login).map(EmployeeView::fullName).orElse(login);
    }

    private static Instant time(String iso) {
        try { return iso == null ? Instant.now() : OffsetDateTime.parse(iso).toInstant(); }
        catch (RuntimeException e) { log.debug("Bad commit timestamp {}", iso); return Instant.now(); }
    }
}
