package com.baracel.portal.leave.service;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.NotificationService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.employee.service.EmployeeService;
import com.baracel.portal.employee.service.EmployeeView;
import com.baracel.portal.leave.domain.*;
import com.baracel.portal.leave.repo.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveService {
    public record BalanceDto(String type, String name, Integer entitled, int used, int pending, Integer left) {}
    public record LeaveRequestDto(UUID id, UUID employeeId, String employeeName, String type, String typeName, LocalDate from,
                                  LocalDate to, int days, String reason, LeaveStatus status, String decidedBy, Instant decidedAt,
                                  String note, Instant createdAt) {}
    public record CreateLeave(String type, LocalDate from, LocalDate to, String reason) {}
    public record Preview(int workingDays, Integer balanceAfter, List<String> overlaps, boolean overlapsOwnRequest) {}
    public record CalendarEntry(UUID employeeId, String name, String initials, String type, LocalDate from, LocalDate to, LeaveStatus status) {}
    public record OutEntry(UUID employeeId, String name, String initials, String typeName, LocalDate to, LocalDate backOn) {}

    private static final List<LeaveStatus> BLOCKING = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveTypeRepository types;
    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final WorkingDays workingDays;
    private final EmployeeService employees;
    private final NotificationService notifications;
    private final Clock clock;

    public LeaveService(LeaveTypeRepository types, LeaveBalanceRepository balances, LeaveRequestRepository requests,
                        WorkingDays workingDays, EmployeeService employees, NotificationService notifications, Clock clock) {
        this.types = types;
        this.balances = balances;
        this.requests = requests;
        this.workingDays = workingDays;
        this.employees = employees;
        this.notifications = notifications;
        this.clock = clock;
    }

    public record LeaveTypeInfo(String code, String name, Integer defaultDays) {}

    @Transactional(readOnly = true)
    public List<LeaveTypeInfo> leaveTypes() {
        return types.findAllByOrderBySortOrderAsc().stream().map(t -> new LeaveTypeInfo(t.code, t.name, t.defaultDays)).toList();
    }

    // ---- balances ----

    @Transactional
    public List<BalanceDto> balances(UUID employeeId) {
        int year = LocalDate.now(clock).getYear();
        List<BalanceDto> out = new ArrayList<>();
        for (LeaveType t : types.findAllByOrderBySortOrderAsc()) {
            if (t.defaultDays == null) { out.add(new BalanceDto(t.code, t.name, null, usedUnpaid(employeeId, t, year), 0, null)); continue; }
            LeaveBalance b = balanceRow(employeeId, t, year);
            int pending = (int) requests.pendingDays(employeeId, t.id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            out.add(new BalanceDto(t.code, t.name, b.entitled, b.used, pending, b.entitled - b.used));
        }
        return out;
    }

    private int usedUnpaid(UUID employeeId, LeaveType t, int year) { return 0; }

    private LeaveBalance balanceRow(UUID employeeId, LeaveType t, int year) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, t.id, year).orElseGet(() -> {
            LeaveBalance b = new LeaveBalance();
            b.employeeId = employeeId;
            b.leaveTypeId = t.id;
            b.year = year;
            b.entitled = t.defaultDays == null ? 0 : t.defaultDays;
            return balances.save(b);
        });
    }

    /** Annual leave left for a set of people (used by the team and records views). */
    @Transactional
    public Map<UUID, Integer> annualLeft(Collection<UUID> employeeIds) {
        LeaveType annual = types.findByCodeIgnoreCase("ANNUAL").orElseThrow();
        int year = LocalDate.now(clock).getYear();
        Map<UUID, Integer> out = new HashMap<>();
        for (UUID id : employeeIds) {
            LeaveBalance b = balanceRow(id, annual, year);
            out.put(id, b.entitled - b.used);
        }
        return out;
    }

    // ---- requests ----

    @Transactional(readOnly = true)
    public Preview preview(CurrentUser me, String typeCode, LocalDate from, LocalDate to) {
        LeaveType t = type(typeCode);
        validateDates(from, to);
        int days = workingDays.count(from, to);
        Integer after = null;
        if (t.defaultDays != null) {
            int year = from.getYear();
            LeaveBalance b = balances.findByEmployeeIdAndLeaveTypeIdAndYear(me.employeeId(), t.id, year).orElse(null);
            int left = (b == null ? t.defaultDays : b.entitled - b.used)
                    - (int) requests.pendingDays(me.employeeId(), t.id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            after = left - days;
        }
        EmployeeView self = employees.require(me.employeeId());
        Set<UUID> mates = employees.inDepartment(self.department()).stream().map(EmployeeView::id)
                .filter(id -> !id.equals(me.employeeId())).collect(Collectors.toSet());
        List<String> overlaps = mates.isEmpty() ? List.of() : names(requests.findOverlapping(mates, from, to, BLOCKING));
        return new Preview(days, after, overlaps, requests.existsOverlap(me.employeeId(), from, to, BLOCKING));
    }

    @Transactional
    public LeaveRequestDto create(CurrentUser me, CreateLeave in) {
        LeaveType t = type(in.type());
        validateDates(in.from(), in.to());
        int days = workingDays.count(in.from(), in.to());
        if (days == 0) throw ApiException.badRequest("no-working-days", "The selected dates contain no working days.");
        if (requests.existsOverlap(me.employeeId(), in.from(), in.to(), BLOCKING))
            throw ApiException.conflict("leave-overlap", "You already have leave in these dates.");
        if (t.defaultDays != null) {
            int year = in.from().getYear();
            LeaveBalance b = balanceRow(me.employeeId(), t, year);
            long pending = requests.pendingDays(me.employeeId(), t.id, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
            if (b.entitled - b.used - pending < days)
                throw ApiException.conflict("insufficient-balance",
                        "Not enough " + t.name.toLowerCase() + " leave left: " + (b.entitled - b.used - pending) + " day(s) available, " + days + " requested.");
        }
        LeaveRequest r = new LeaveRequest();
        r.id = UUID.randomUUID();
        r.employeeId = me.employeeId();
        r.leaveTypeId = t.id;
        r.fromDate = in.from();
        r.toDate = in.to();
        r.days = days;
        r.reason = in.reason();
        r.status = LeaveStatus.PENDING;
        requests.saveAndFlush(r);

        EmployeeView self = employees.require(me.employeeId());
        employees.find(self.managerId()).ifPresent(m -> notifications.notify(m.id(), m.email(), "LEAVE_SUBMITTED",
                "Leave request from " + self.fullName(),
                self.fullName() + " requested " + days + " day(s) of " + t.name.toLowerCase() + " leave, " + in.from() + " to " + in.to() + ".",
                "/leave"));
        return dto(r, t, Map.of(self.id(), self), Map.of());
    }

    @Transactional
    public LeaveRequestDto cancel(CurrentUser me, UUID id) {
        LeaveRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Leave request not found"));
        if (!r.employeeId.equals(me.employeeId())) throw ApiException.notFound("Leave request not found");
        if (r.status != LeaveStatus.PENDING) throw ApiException.conflict("not-pending", "Only a pending request can be cancelled.");
        r.status = LeaveStatus.CANCELLED;
        return one(r);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> list(CurrentUser me, String scope, String statusFilter) {
        if ("mine".equalsIgnoreCase(scope)) {
            return map(requests.findByEmployeeIdOrderByFromDateDesc(me.employeeId()));
        }
        if (!"approvals".equalsIgnoreCase(scope)) throw ApiException.badRequest("invalid-scope", "scope must be mine or approvals");
        if (!me.has("leave:approve")) throw ApiException.forbidden("You cannot approve leave.");
        Specification<LeaveRequest> spec = (root, q, cb) -> cb.notEqual(root.get("employeeId"), me.employeeId());
        if (!me.has("employee:records")) {
            // A manager sees only the requests of their direct reports.
            Set<UUID> reports = employees.directReports(me.employeeId()).stream().map(EmployeeView::id).collect(Collectors.toSet());
            if (reports.isEmpty()) return List.of();
            spec = spec.and((root, q, cb) -> root.get("employeeId").in(reports));
        }
        if (statusFilter == null || statusFilter.isBlank() || "PENDING".equalsIgnoreCase(statusFilter))
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), LeaveStatus.PENDING));
        else if (!"ALL".equalsIgnoreCase(statusFilter)) {
            LeaveStatus s = LeaveStatus.valueOf(statusFilter.toUpperCase());
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), s));
        }
        return map(requests.findAll(spec, org.springframework.data.domain.Sort.by("fromDate")));
    }

    @Transactional
    public LeaveRequestDto approve(CurrentUser me, UUID id, String note) {
        LeaveRequest r = decidable(me, id);
        r.status = LeaveStatus.APPROVED;
        stamp(r, me, note);
        LeaveType t = types.findById(r.leaveTypeId).orElseThrow();
        if (t.defaultDays != null) balanceRow(r.employeeId, t, r.fromDate.getYear()).used += r.days;
        tell(r, t, "approved");
        return one(r);
    }

    @Transactional
    public LeaveRequestDto reject(CurrentUser me, UUID id, String note) {
        LeaveRequest r = decidable(me, id);
        r.status = LeaveStatus.REJECTED;
        stamp(r, me, note);
        tell(r, types.findById(r.leaveTypeId).orElseThrow(), "rejected");
        return one(r);
    }

    private LeaveRequest decidable(CurrentUser me, UUID id) {
        if (!me.has("leave:approve")) throw ApiException.forbidden("You cannot approve leave.");
        LeaveRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Leave request not found"));
        if (r.employeeId.equals(me.employeeId())) throw ApiException.forbidden("Nobody can decide their own leave request.");
        if (!me.has("employee:records")) {
            EmployeeView requester = employees.require(r.employeeId);
            if (!me.employeeId().equals(requester.managerId()))
                throw ApiException.forbidden("You can only decide leave for your direct reports.");
        }
        if (r.status != LeaveStatus.PENDING) throw ApiException.conflict("not-pending", "This request was already decided.");
        return r;
    }

    private void stamp(LeaveRequest r, CurrentUser me, String note) {
        r.decidedBy = me.employeeId();
        r.decidedAt = Instant.now(clock);
        r.note = note;
    }

    private void tell(LeaveRequest r, LeaveType t, String verb) {
        EmployeeView who = employees.require(r.employeeId);
        notifications.notify(who.id(), who.email(), "LEAVE_" + verb.toUpperCase(),
                "Your " + t.name.toLowerCase() + " leave was " + verb,
                "Your request for " + r.fromDate + " to " + r.toDate + " (" + r.days + " day(s)) was " + verb
                        + (r.note == null || r.note.isBlank() ? "." : ". Note: " + r.note),
                "/leave");
    }

    // ---- calendar, out today, status job ----

    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(CurrentUser me, YearMonth month) {
        EmployeeView self = employees.require(me.employeeId());
        Map<UUID, EmployeeView> people = new HashMap<>();
        employees.inDepartment(self.department()).forEach(v -> people.put(v.id(), v));
        people.put(self.id(), self);
        List<LeaveType> all = types.findAll();
        Map<Long, String> typeCode = all.stream().collect(Collectors.toMap(x -> x.id, x -> x.code));
        return requests.findOverlapping(people.keySet(), month.atDay(1), month.atEndOfMonth(), BLOCKING).stream()
                .map(r -> new CalendarEntry(r.employeeId, people.get(r.employeeId).fullName(), people.get(r.employeeId).initials(),
                        typeCode.get(r.leaveTypeId), r.fromDate, r.toDate, r.status)).toList();
    }

    @Transactional(readOnly = true)
    public List<OutEntry> outOn(LocalDate date) {
        List<LeaveRequest> covering = requests.findCovering(date, LeaveStatus.APPROVED);
        Map<UUID, EmployeeView> people = employees.views(covering.stream().map(r -> r.employeeId).toList());
        Map<Long, String> typeName = types.findAll().stream().collect(Collectors.toMap(x -> x.id, x -> x.name));
        return covering.stream().map(r -> {
            EmployeeView v = people.get(r.employeeId);
            return new OutEntry(v.id(), v.fullName(), v.initials(), typeName.get(r.leaveTypeId), r.toDate, workingDays.nextWorkingDay(r.toDate));
        }).sorted(Comparator.comparing(OutEntry::name)).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> recentFor(UUID employeeId) {
        return map(requests.findTop5ByEmployeeIdAndStatusInOrderByFromDateDesc(employeeId,
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED)));
    }

    @Transactional
    public int syncEmployeeStatuses() {
        Set<UUID> out = requests.findCovering(LocalDate.now(clock), LeaveStatus.APPROVED).stream().map(r -> r.employeeId)
                .collect(Collectors.toSet());
        return employees.syncLeaveStatus(out);
    }

    // ---- helpers ----

    private LeaveType type(String code) {
        return types.findByCodeIgnoreCase(code == null ? "" : code.replace('-', '_'))
                .orElseThrow(() -> ApiException.badRequest("unknown-leave-type", "Unknown leave type: " + code));
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw ApiException.badRequest("validation", "from and to are required");
        if (to.isBefore(from)) throw ApiException.badRequest("invalid-dates", "The end date is before the start date.");
    }

    private List<String> names(List<LeaveRequest> rs) {
        Map<UUID, EmployeeView> v = employees.views(rs.stream().map(r -> r.employeeId).toList());
        return rs.stream().map(r -> v.get(r.employeeId).fullName()).distinct().toList();
    }

    private LeaveRequestDto one(LeaveRequest r) { return map(List.of(r)).get(0); }

    private List<LeaveRequestDto> map(List<LeaveRequest> rs) {
        Set<UUID> ids = new HashSet<>();
        rs.forEach(r -> { ids.add(r.employeeId); if (r.decidedBy != null) ids.add(r.decidedBy); });
        Map<UUID, EmployeeView> people = employees.views(ids);
        Map<Long, LeaveType> byId = types.findAll().stream().collect(Collectors.toMap(x -> x.id, x -> x));
        return rs.stream().map(r -> dto(r, byId.get(r.leaveTypeId), people, people)).toList();
    }

    private LeaveRequestDto dto(LeaveRequest r, LeaveType t, Map<UUID, EmployeeView> people, Map<UUID, EmployeeView> deciders) {
        EmployeeView who = people.get(r.employeeId);
        EmployeeView by = r.decidedBy == null ? null : deciders.get(r.decidedBy);
        return new LeaveRequestDto(r.id, r.employeeId, who == null ? null : who.fullName(), t.code, t.name, r.fromDate, r.toDate,
                r.days, r.reason, r.status, by == null ? null : by.fullName(), r.decidedAt, r.note, r.createdAt);
    }
}
