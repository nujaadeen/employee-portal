package com.baracel.portal.task.service;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.NotificationService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.task.domain.*;
import com.baracel.portal.task.repo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    public record ProjectDto(Long id, String key, String name, int members, SprintDto activeSprint) {}
    public record SprintDto(Long id, String name, LocalDate startsOn, LocalDate endsOn, boolean active) {}
    public record TaskView(Long id, String key, String title, String label, TaskStatus status, Priority priority, LocalDate dueDate,
                           UUID assigneeId, String assigneeName, String assigneeInitials, int subtasksDone, int subtasksTotal,
                           Long sprintId) {}
    public record SubtaskDto(Long id, String title, boolean done) {}
    public record CommentDto(Long id, UUID authorId, String author, String body, Instant createdAt) {}
    public record ActivityDto(Long id, String actor, String kind, String message, Instant createdAt) {}
    public record TaskDetailDto(TaskView task, String projectKey, String description, UUID reporterId, String reporterName,
                                String sprintName, List<SubtaskDto> subtasks, List<CommentDto> comments, List<ActivityDto> activity) {}
    public record BoardData(ProjectDto project, SprintDto sprint, List<SprintDto> sprints, List<TaskView> tasks) {}
    public record TaskRef(Long id, String key, TaskStatus status, Long projectId, String title, UUID assigneeId, UUID reporterId) {}

    public record CreateTask(String projectKey, String title, String description, Priority priority, UUID assigneeId,
                             LocalDate dueDate, String label, Long sprintId) {}
    public record UpdateTask(String title, String description, Priority priority, UUID assigneeId, LocalDate dueDate,
                             String label, Long sprintId) {}

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final SprintRepository sprints;
    private final TaskRepository tasks;
    private final SubtaskRepository subtasks;
    private final TaskCommentRepository comments;
    private final TaskActivityRepository activities;
    private final EmployeeService employees;
    private final NotificationService notifications;

    public TaskService(ProjectRepository projects, ProjectMemberRepository members, SprintRepository sprints, TaskRepository tasks,
                       SubtaskRepository subtasks, TaskCommentRepository comments, TaskActivityRepository activities,
                       EmployeeService employees, NotificationService notifications) {
        this.notifications = notifications;
        this.projects = projects;
        this.members = members;
        this.sprints = sprints;
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.comments = comments;
        this.activities = activities;
        this.employees = employees;
    }

    // ---- projects and board ----

    @Transactional(readOnly = true)
    public List<ProjectDto> projectsFor(CurrentUser me) {
        List<Long> ids = members.findByIdEmployeeId(me.employeeId()).stream().map(m -> m.id.projectId()).toList();
        return projects.findAllById(ids).stream().sorted(Comparator.comparing(p -> p.key)).map(this::projectDto).toList();
    }

    @Transactional(readOnly = true)
    public BoardData board(CurrentUser me, String projectKey, Long sprintId) {
        Project p = memberProject(me, projectKey);
        List<Sprint> all = sprints.findByProjectIdOrderByStartsOnDesc(p.id);
        Sprint sprint = sprintId != null
                ? all.stream().filter(s -> s.id.equals(sprintId)).findFirst().orElseThrow(() -> ApiException.notFound("Sprint not found"))
                : all.stream().filter(s -> s.active).findFirst().orElse(all.isEmpty() ? null : all.get(0));
        List<Task> list = sprint == null ? tasks.findByProjectIdAndSprintIdIsNullOrderByNumberAsc(p.id)
                : tasks.findBySprintIdOrderByNumberAsc(sprint.id);
        return new BoardData(projectDto(p), sprint == null ? null : sprintDto(sprint), all.stream().map(this::sprintDto).toList(), views(list));
    }

    // ---- tasks ----

    @Transactional(readOnly = true)
    public TaskDetailDto detail(CurrentUser me, String key) {
        Task t = memberTask(me, key);
        Project p = projects.findById(t.projectId).orElseThrow();
        Set<UUID> ids = new HashSet<>();
        if (t.reporterId != null) ids.add(t.reporterId);
        List<TaskComment> cs = comments.findByTaskIdOrderByCreatedAtAsc(t.id);
        cs.forEach(c -> ids.add(c.authorId));
        Map<UUID, EmployeeView> people = employees.views(ids);
        String sprintName = t.sprintId == null ? null : sprints.findById(t.sprintId).map(s -> s.name).orElse(null);
        return new TaskDetailDto(views(List.of(t)).get(0), p.key, t.description, t.reporterId,
                t.reporterId == null || !people.containsKey(t.reporterId) ? null : people.get(t.reporterId).fullName(), sprintName,
                subtasks.findByTaskIdOrderByPositionAscIdAsc(t.id).stream().map(s -> new SubtaskDto(s.id, s.title, s.done)).toList(),
                cs.stream().map(c -> new CommentDto(c.id, c.authorId, people.get(c.authorId).fullName(), c.body, c.createdAt)).toList(),
                activities.findByTaskIdOrderByCreatedAtDescIdDesc(t.id).stream()
                        .map(a -> new ActivityDto(a.id, a.actorName, a.kind, a.message, a.createdAt)).toList());
    }

    @Transactional
    public TaskView create(CurrentUser me, CreateTask in) {
        Project p = memberProject(me, in.projectKey());
        if (in.title() == null || in.title().isBlank()) throw ApiException.badRequest("validation", "title is required");
        Project locked = projects.lockById(p.id).orElseThrow();
        locked.taskSeq++;
        Task t = new Task();
        t.projectId = p.id;
        t.number = locked.taskSeq;
        t.taskKey = p.key + "-" + locked.taskSeq;
        t.title = in.title().trim();
        t.description = in.description();
        t.status = TaskStatus.BACKLOG;
        t.priority = in.priority() == null ? Priority.MEDIUM : in.priority();
        t.reporterId = me.employeeId();
        t.dueDate = in.dueDate();
        t.label = in.label();
        if (in.assigneeId() != null) { requireMember(p.id, in.assigneeId()); t.assigneeId = in.assigneeId(); }
        if (in.sprintId() != null) t.sprintId = sprintOf(p.id, in.sprintId()).id;
        else sprints.findFirstByProjectIdAndActiveTrue(p.id).ifPresent(s -> t.sprintId = s.id);
        tasks.saveAndFlush(t);
        activity(t.id, me.employeeId(), me.name(), "CREATED", "created this task");
        assigned(t, me);
        return views(List.of(t)).get(0);
    }

    @Transactional
    public TaskView update(CurrentUser me, String key, UpdateTask in) {
        Task t = memberTask(me, key);
        if (in.title() != null) { if (in.title().isBlank()) throw ApiException.badRequest("validation", "title cannot be empty"); t.title = in.title().trim(); }
        if (in.description() != null) t.description = in.description();
        if (in.priority() != null && in.priority() != t.priority) {
            activity(t.id, me.employeeId(), me.name(), "PRIORITY", "changed priority from " + t.priority + " to " + in.priority());
            t.priority = in.priority();
        }
        if (in.assigneeId() != null && !in.assigneeId().equals(t.assigneeId)) {
            requireMember(t.projectId, in.assigneeId());
            t.assigneeId = in.assigneeId();
            activity(t.id, me.employeeId(), me.name(), "ASSIGNEE", "assigned this to " + employees.require(in.assigneeId()).fullName());
            assigned(t, me);
        }
        if (in.dueDate() != null) t.dueDate = in.dueDate();
        if (in.label() != null) t.label = in.label();
        if (in.sprintId() != null) t.sprintId = sprintOf(t.projectId, in.sprintId()).id;
        return views(List.of(t)).get(0);
    }

    @Transactional
    public TaskView changeStatus(CurrentUser me, String key, TaskStatus to) {
        Task t = memberTask(me, key);
        if (to == null) throw ApiException.badRequest("validation", "status is required");
        if (t.status != to) {
            activity(t.id, me.employeeId(), me.name(), "STATUS", "moved this from " + t.status.label + " to " + to.label);
            t.status = to;
        }
        return views(List.of(t)).get(0);
    }

    // ---- subtasks and comments ----

    @Transactional
    public SubtaskDto addSubtask(CurrentUser me, String key, String title) {
        Task t = memberTask(me, key);
        if (title == null || title.isBlank()) throw ApiException.badRequest("validation", "title is required");
        Subtask s = new Subtask();
        s.taskId = t.id;
        s.title = title.trim();
        s.position = subtasks.findByTaskIdOrderByPositionAscIdAsc(t.id).size();
        subtasks.save(s);
        return new SubtaskDto(s.id, s.title, s.done);
    }

    @Transactional
    public SubtaskDto updateSubtask(CurrentUser me, String key, Long id, String title, Boolean done) {
        Task t = memberTask(me, key);
        Subtask s = subtasks.findById(id).filter(x -> x.taskId.equals(t.id)).orElseThrow(() -> ApiException.notFound("Subtask not found"));
        if (title != null && !title.isBlank()) s.title = title.trim();
        if (done != null && done != s.done) {
            s.done = done;
            activity(t.id, me.employeeId(), me.name(), "SUBTASK", (done ? "completed" : "reopened") + " subtask \"" + s.title + "\"");
        }
        return new SubtaskDto(s.id, s.title, s.done);
    }

    @Transactional
    public void deleteSubtask(CurrentUser me, String key, Long id) {
        Task t = memberTask(me, key);
        Subtask s = subtasks.findById(id).filter(x -> x.taskId.equals(t.id)).orElseThrow(() -> ApiException.notFound("Subtask not found"));
        subtasks.delete(s);
    }

    @Transactional
    public CommentDto addComment(CurrentUser me, String key, String body) {
        Task t = memberTask(me, key);
        if (body == null || body.isBlank()) throw ApiException.badRequest("validation", "body is required");
        TaskComment c = new TaskComment();
        c.taskId = t.id;
        c.authorId = me.employeeId();
        c.body = body.trim();
        comments.save(c);
        activity(t.id, me.employeeId(), me.name(), "COMMENT", "commented: " + c.body);
        for (UUID id : new LinkedHashSet<>(Arrays.asList(t.assigneeId, t.reporterId))) {
            if (id == null || id.equals(me.employeeId())) continue;
            employees.find(id).ifPresent(e -> notifications.notify(e.id(), e.email(), "TASK_COMMENT",
                    me.name() + " commented on " + t.taskKey, c.body, "/tasks/" + t.taskKey));
        }
        return new CommentDto(c.id, c.authorId, me.name(), c.body, c.createdAt);
    }

    // ---- lists for the dashboard and "my tasks" ----

    @Transactional(readOnly = true)
    public List<TaskView> mine(CurrentUser me, LocalDate dueBefore, boolean openOnly) {
        Set<Long> memberOf = members.findByIdEmployeeId(me.employeeId()).stream().map(m -> m.id.projectId()).collect(Collectors.toSet());
        List<Task> list = tasks.findByAssigneeId(me.employeeId()).stream()
                .filter(t -> memberOf.contains(t.projectId))
                .filter(t -> !openOnly || t.status != TaskStatus.DONE)
                .filter(t -> dueBefore == null || (t.dueDate != null && !t.dueDate.isAfter(dueBefore)))
                .sorted(Comparator.comparing((Task t) -> t.dueDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(t -> t.number))
                .toList();
        return views(list);
    }

    // ---- hooks for the Gitea module (no user session; the webhook is signed instead) ----

    @Transactional(readOnly = true)
    public Map<String, TaskRef> existing(Collection<String> keys) {
        Map<String, TaskRef> out = new HashMap<>();
        if (keys.isEmpty()) return out;
        for (Task t : tasks.findByTaskKeyIn(keys)) out.put(t.taskKey, ref(t));
        return out;
    }

    /** Resolves a task the caller may work on (project member), for the Git endpoints. */
    @Transactional(readOnly = true)
    public TaskRef requireMemberRef(CurrentUser me, String key) { return ref(memberTask(me, key)); }

    @Transactional
    public void addActivity(Long taskId, String actorName, String kind, String message) {
        activity(taskId, null, actorName, kind, message);
    }

    @Transactional
    public void addActivity(Long taskId, UUID actorId, String actorName, String kind, String message) {
        activity(taskId, actorId, actorName, kind, message);
    }

    /** Moves a task only when it is currently in the expected status. Returns true when it moved. */
    @Transactional
    public boolean moveIf(Long taskId, TaskStatus from, TaskStatus to, String actorName, String reason) {
        Task t = tasks.findById(taskId).orElse(null);
        if (t == null || t.status != from) return false;
        t.status = to;
        activity(t.id, null, actorName, "STATUS", "moved this from " + from.label + " to " + to.label + " (" + reason + ")");
        return true;
    }

    @Transactional(readOnly = true)
    public Set<Long> memberProjectIds(CurrentUser me) {
        return members.findByIdEmployeeId(me.employeeId()).stream().map(m -> m.id.projectId()).collect(Collectors.toSet());
    }

    // ---- helpers ----

    private Project memberProject(CurrentUser me, String key) {
        Project p = projects.findByKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Project not found"));
        // Not a member: answer 404 so project names do not leak.
        if (!members.existsByIdProjectIdAndIdEmployeeId(p.id, me.employeeId())) throw ApiException.notFound("Project not found");
        return p;
    }

    private Task memberTask(CurrentUser me, String key) {
        Task t = tasks.findByTaskKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Task not found"));
        if (!members.existsByIdProjectIdAndIdEmployeeId(t.projectId, me.employeeId())) throw ApiException.notFound("Task not found");
        return t;
    }

    private void requireMember(Long projectId, UUID employeeId) {
        if (!members.existsByIdProjectIdAndIdEmployeeId(projectId, employeeId))
            throw ApiException.badRequest("not-a-member", "That person is not a member of this project.");
    }

    private Sprint sprintOf(Long projectId, Long sprintId) {
        return sprints.findById(sprintId).filter(s -> s.projectId.equals(projectId))
                .orElseThrow(() -> ApiException.badRequest("invalid-sprint", "Sprint does not belong to this project."));
    }

    private void activity(Long taskId, UUID actorId, String actorName, String kind, String message) {
        TaskActivity a = new TaskActivity();
        a.taskId = taskId;
        a.actorId = actorId;
        a.actorName = actorName;
        a.kind = kind;
        a.message = message;
        activities.save(a);
    }

    /** Tells the assignee about a new assignment. Nobody is notified about assigning a task to themselves. */
    private void assigned(Task t, CurrentUser by) {
        if (t.assigneeId == null || t.assigneeId.equals(by.employeeId())) return;
        employees.find(t.assigneeId).ifPresent(e -> notifications.notify(e.id(), e.email(), "TASK_ASSIGNED",
                t.taskKey + " was assigned to you", by.name() + " assigned you \"" + t.title + "\".", "/tasks/" + t.taskKey));
    }

    private static TaskRef ref(Task t) { return new TaskRef(t.id, t.taskKey, t.status, t.projectId, t.title, t.assigneeId, t.reporterId); }

    private ProjectDto projectDto(Project p) {
        return new ProjectDto(p.id, p.key, p.name, members.findByIdProjectId(p.id).size(),
                sprints.findFirstByProjectIdAndActiveTrue(p.id).map(this::sprintDto).orElse(null));
    }

    private SprintDto sprintDto(Sprint s) { return new SprintDto(s.id, s.name, s.startsOn, s.endsOn, s.active); }

    private List<TaskView> views(List<Task> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, EmployeeView> people = employees.views(list.stream().map(t -> t.assigneeId).toList());
        Map<Long, List<Subtask>> subs = subtasks.findByTaskIdIn(list.stream().map(t -> t.id).toList()).stream()
                .collect(Collectors.groupingBy(s -> s.taskId));
        return list.stream().map(t -> {
            EmployeeView a = t.assigneeId == null ? null : people.get(t.assigneeId);
            List<Subtask> s = subs.getOrDefault(t.id, List.of());
            return new TaskView(t.id, t.taskKey, t.title, t.label, t.status, t.priority, t.dueDate, t.assigneeId,
                    a == null ? null : a.fullName(), a == null ? null : a.initials(),
                    (int) s.stream().filter(x -> x.done).count(), s.size(), t.sprintId);
        }).toList();
    }
}
