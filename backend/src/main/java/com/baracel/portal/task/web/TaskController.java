package com.baracel.portal.task.web;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.gitea.service.GiteaService;
import com.baracel.portal.gitea.service.GiteaService.GitDetail;
import com.baracel.portal.gitea.service.GiteaService.GitSummary;
import com.baracel.portal.task.domain.Priority;
import com.baracel.portal.task.domain.TaskStatus;
import com.baracel.portal.task.service.TaskService;
import com.baracel.portal.task.service.TaskService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Tasks")
public class TaskController {
    public record TaskCard(TaskView task, GitSummary git) {}
    public record Column(TaskStatus status, String label, int count, List<TaskCard> tasks) {}
    public record Board(ProjectDto project, SprintDto sprint, List<SprintDto> sprints, int taskCount, int linkedToGitea,
                        Instant lastSyncedAt, List<Column> columns) {}
    public record TaskDetailResponse(TaskDetailDto task, GitDetail git) {}

    public record CreateTaskRequest(@NotBlank String projectKey, @NotBlank String title, String description, Priority priority,
                                    UUID assigneeId, LocalDate dueDate, String label, Long sprintId) {}
    public record UpdateTaskRequest(String title, String description, Priority priority, UUID assigneeId, LocalDate dueDate,
                                    String label, Long sprintId) {}
    public record StatusRequest(@NotNull TaskStatus status) {}
    public record SubtaskRequest(@NotBlank String title) {}
    public record SubtaskUpdate(String title, Boolean done) {}
    public record CommentRequest(@NotBlank String body) {}

    private final TaskService service;
    private final GiteaService gitea;

    public TaskController(TaskService service, GiteaService gitea) {
        this.service = service;
        this.gitea = gitea;
    }

    @GetMapping("/api/projects")
    @PreAuthorize("hasAuthority('task:view')")
    public List<ProjectDto> projects(CurrentUser me) { return service.projectsFor(me); }

    @GetMapping("/api/projects/{key}/board")
    @PreAuthorize("hasAuthority('task:view')")
    public Board board(CurrentUser me, @PathVariable String key, @RequestParam(required = false) Long sprint) {
        BoardData data = service.board(me, key, sprint);
        List<Long> ids = data.tasks().stream().map(TaskView::id).toList();
        Map<Long, GitSummary> git = gitea.summaries(ids);
        List<Column> columns = new ArrayList<>();
        for (TaskStatus s : TaskStatus.values()) {
            List<TaskCard> cards = data.tasks().stream().filter(t -> t.status() == s).map(t -> new TaskCard(t, git.get(t.id()))).toList();
            columns.add(new Column(s, s.label, cards.size(), cards));
        }
        return new Board(data.project(), data.sprint(), data.sprints(), data.tasks().size(),
                (int) data.tasks().stream().filter(t -> git.containsKey(t.id())).count(), gitea.lastSyncedAt(ids), columns);
    }

    @GetMapping("/api/tasks")
    @PreAuthorize("hasAuthority('task:view')")
    public List<TaskCard> mine(CurrentUser me, @RequestParam(defaultValue = "me") String assignee,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
                               @RequestParam(defaultValue = "true") boolean open) {
        List<TaskView> tasks = service.mine(me, dueBefore, open);
        Map<Long, GitSummary> git = gitea.summaries(tasks.stream().map(TaskView::id).toList());
        return tasks.stream().map(t -> new TaskCard(t, git.get(t.id()))).toList();
    }

    @GetMapping("/api/tasks/{key}")
    @PreAuthorize("hasAuthority('task:view')")
    public TaskDetailResponse detail(CurrentUser me, @PathVariable String key) {
        TaskDetailDto d = service.detail(me, key);
        com.baracel.portal.task.service.TaskService.TaskRef ref = service.requireMemberRef(me, key);
        return new TaskDetailResponse(d, gitea.detail(ref.id(), ref.projectId()));
    }

    @PostMapping("/api/tasks")
    @PreAuthorize("hasAuthority('task:create')")
    public ResponseEntity<TaskView> create(CurrentUser me, @Valid @RequestBody CreateTaskRequest b) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me,
                new CreateTask(b.projectKey(), b.title(), b.description(), b.priority(), b.assigneeId(), b.dueDate(), b.label(), b.sprintId())));
    }

    @PatchMapping("/api/tasks/{key}")
    @PreAuthorize("hasAuthority('task:edit')")
    public TaskView update(CurrentUser me, @PathVariable String key, @RequestBody UpdateTaskRequest b) {
        return service.update(me, key, new UpdateTask(b.title(), b.description(), b.priority(), b.assigneeId(), b.dueDate(), b.label(), b.sprintId()));
    }

    @PatchMapping("/api/tasks/{key}/status")
    @PreAuthorize("hasAuthority('task:edit')")
    public TaskView status(CurrentUser me, @PathVariable String key, @Valid @RequestBody StatusRequest b) {
        return service.changeStatus(me, key, b.status());
    }

    @PostMapping("/api/tasks/{key}/subtasks")
    @PreAuthorize("hasAuthority('task:edit')")
    public ResponseEntity<SubtaskDto> addSubtask(CurrentUser me, @PathVariable String key, @Valid @RequestBody SubtaskRequest b) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addSubtask(me, key, b.title()));
    }

    @PatchMapping("/api/tasks/{key}/subtasks/{id}")
    @PreAuthorize("hasAuthority('task:edit')")
    public SubtaskDto updateSubtask(CurrentUser me, @PathVariable String key, @PathVariable Long id, @RequestBody SubtaskUpdate b) {
        return service.updateSubtask(me, key, id, b.title(), b.done());
    }

    @DeleteMapping("/api/tasks/{key}/subtasks/{id}")
    @PreAuthorize("hasAuthority('task:edit')")
    public ResponseEntity<Void> deleteSubtask(CurrentUser me, @PathVariable String key, @PathVariable Long id) {
        service.deleteSubtask(me, key, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tasks/{key}/comments")
    @PreAuthorize("hasAuthority('task:edit')")
    public ResponseEntity<CommentDto> comment(CurrentUser me, @PathVariable String key, @Valid @RequestBody CommentRequest b) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addComment(me, key, b.body()));
    }
}
