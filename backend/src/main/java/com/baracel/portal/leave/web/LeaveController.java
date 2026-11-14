package com.baracel.portal.leave.web;

import com.baracel.portal.common.web.Nullable;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.leave.service.LeaveService;
import com.baracel.portal.leave.service.LeaveService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leave")
@Tag(name = "Leave")
public class LeaveController {
    private final LeaveService service;

    public LeaveController(LeaveService service) { this.service = service; }

    public record CreateLeaveRequest(@NotBlank String type, @NotNull LocalDate from, @NotNull LocalDate to, @Nullable String reason) {}
    public record DecisionRequest(@Nullable String note) {}

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('leave:request')")
    public List<BalanceDto> balances(CurrentUser me) { return service.balances(me.employeeId()); }

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('leave:request')")
    public Preview preview(CurrentUser me, @RequestParam String type,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.preview(me, type, from, to);
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('leave:request')")
    public ResponseEntity<LeaveRequestDto> create(CurrentUser me, @Valid @RequestBody CreateLeaveRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(me, new CreateLeave(body.type(), body.from(), body.to(), body.reason())));
    }

    @DeleteMapping("/requests/{id}")
    @PreAuthorize("hasAuthority('leave:request')")
    public LeaveRequestDto cancel(CurrentUser me, @PathVariable UUID id) { return service.cancel(me, id); }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('leave:request')")
    public List<LeaveRequestDto> list(CurrentUser me, @RequestParam(defaultValue = "mine") String scope,
                                      @RequestParam(required = false) String status) {
        return service.list(me, scope, status);
    }

    @PostMapping("/requests/{id}/approve")
    @PreAuthorize("hasAuthority('leave:approve')")
    public LeaveRequestDto approve(CurrentUser me, @PathVariable UUID id, @RequestBody(required = false) DecisionRequest body) {
        return service.approve(me, id, body == null ? null : body.note());
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAuthority('leave:approve')")
    public LeaveRequestDto reject(CurrentUser me, @PathVariable UUID id, @RequestBody(required = false) DecisionRequest body) {
        return service.reject(me, id, body == null ? null : body.note());
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('leave:request')")
    public List<CalendarEntry> calendar(CurrentUser me, @RequestParam String month) {
        return service.calendar(me, YearMonth.parse(month));
    }
}
