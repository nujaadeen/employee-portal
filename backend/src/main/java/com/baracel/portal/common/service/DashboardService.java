package com.baracel.portal.common.service;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.common.domain.Announcement;
import com.baracel.portal.common.repo.AnnouncementRepository;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.gitea.service.GiteaService;
import com.baracel.portal.leave.service.LeaveService;
import com.baracel.portal.leave.service.LeaveService.BalanceDto;
import com.baracel.portal.leave.service.LeaveService.OutEntry;
import com.baracel.portal.task.domain.TaskStatus;
import com.baracel.portal.task.service.TaskService;
import com.baracel.portal.task.service.TaskService.TaskView;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregation layer for the dashboard. It calls the other modules' services, never their repositories. */
@Service
public class DashboardService {
    public record AnnualLeave(int left, int entitled) {}
    public record OpenTasks(int count, int dueThisWeek) {}
    public record DueTask(String key, String title, LocalDate dueDate, TaskStatus status, @Nullable String label) {}
    public record AnnouncementDto(Long id, String title, @Nullable String body, String postedBy, Instant createdAt) {}
    public record Dashboard(String firstName, LocalDate today, @Nullable String department, @Nullable AnnualLeave annualLeave, OpenTasks openTasks,
                            List<DueTask> nextDue, int pullRequestsAwaitingReview, List<OutEntry> outToday,
                            List<AnnouncementDto> announcements) {}

    private final LeaveService leave;
    private final TaskService tasks;
    private final GiteaService gitea;
    private final EmployeeService employees;
    private final AnnouncementRepository announcements;
    private final Clock clock;

    public DashboardService(LeaveService leave, TaskService tasks, GiteaService gitea, EmployeeService employees,
                            AnnouncementRepository announcements, Clock clock) {
        this.leave = leave;
        this.tasks = tasks;
        this.gitea = gitea;
        this.employees = employees;
        this.announcements = announcements;
        this.clock = clock;
    }

    @Transactional
    public Dashboard dashboard(CurrentUser me) {
        LocalDate today = LocalDate.now(clock);
        EmployeeView self = employees.require(me.employeeId());
        BalanceDto annual = leave.balances(me.employeeId()).stream().filter(b -> "ANNUAL".equals(b.type())).findFirst().orElse(null);
        List<TaskView> open = me.has("task:view") ? tasks.mine(me, null, true) : List.of();
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        int dueThisWeek = (int) open.stream().filter(t -> t.dueDate() != null && !t.dueDate().isAfter(endOfWeek)).count();
        List<DueTask> next = open.stream().filter(t -> t.dueDate() != null).limit(4)
                .map(t -> new DueTask(t.key(), t.title(), t.dueDate(), t.status(), t.label())).toList();
        return new Dashboard(self.firstName(), today, self.department(),
                annual == null ? null : new AnnualLeave(annual.left() == null ? 0 : annual.left(), annual.entitled() == null ? 0 : annual.entitled()),
                new OpenTasks(open.size(), dueThisWeek), next, gitea.pendingReviews(self.giteaUsername()), leave.outOn(today),
                announcements.findTop20ByOrderByCreatedAtDesc().stream().limit(5).map(DashboardService::dto).toList());
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> announcements() {
        return announcements.findTop20ByOrderByCreatedAtDesc().stream().map(DashboardService::dto).toList();
    }

    @Transactional
    public AnnouncementDto announce(CurrentUser me, String title, String body, String postedBy) {
        Announcement a = new Announcement();
        a.title = title;
        a.body = body;
        a.postedBy = postedBy == null || postedBy.isBlank() ? "HR" : postedBy;
        a.authorId = me.employeeId();
        return dto(announcements.save(a));
    }

    private static AnnouncementDto dto(Announcement a) { return new AnnouncementDto(a.id, a.title, a.body, a.postedBy, a.createdAt); }
}
