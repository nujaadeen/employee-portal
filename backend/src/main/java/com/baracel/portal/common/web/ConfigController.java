package com.baracel.portal.common.web;

import com.baracel.portal.gitea.service.GiteaService;
import com.baracel.portal.leave.service.LeaveService;
import com.baracel.portal.leave.service.LeaveService.LeaveTypeInfo;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public settings the frontend needs. No secrets. Open to every signed-in person except PENDING_APPROVAL (the filter chain refuses those). */
@RestController
@Tag(name = "Config")
public class ConfigController {
    public record PublicConfig(String appName, String giteaUrl, List<LeaveTypeInfo> leaveTypes) {}

    private final GiteaService gitea;
    private final LeaveService leave;

    public ConfigController(GiteaService gitea, LeaveService leave) {
        this.gitea = gitea;
        this.leave = leave;
    }

    @GetMapping("/api/config")
    public PublicConfig config() { return new PublicConfig("Baracel", gitea.publicUrl(), leave.leaveTypes()); }
}
