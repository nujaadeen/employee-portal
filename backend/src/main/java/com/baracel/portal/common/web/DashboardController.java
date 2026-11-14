package com.baracel.portal.common.web;

import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.DashboardService;
import com.baracel.portal.common.service.DashboardService.AnnouncementDto;
import com.baracel.portal.common.service.DashboardService.Dashboard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Dashboard")
public class DashboardController {
    public record AnnouncementRequest(@NotBlank String title, @Nullable String body, @Nullable String postedBy) {}

    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/api/dashboard")
    public Dashboard dashboard(CurrentUser me) { return service.dashboard(me); }

    @GetMapping("/api/announcements")
    public List<AnnouncementDto> announcements() { return service.announcements(); }

    @PostMapping("/api/announcements")
    @PreAuthorize("hasAuthority('employee:manage')")
    public ResponseEntity<AnnouncementDto> announce(CurrentUser me, @Valid @RequestBody AnnouncementRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.announce(me, body.title(), body.body(), body.postedBy()));
    }
}
