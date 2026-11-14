package com.baracel.portal.access.web;

import com.baracel.portal.access.service.AccessService;
import com.baracel.portal.access.service.AccessService.*;
import com.baracel.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/access")
@PreAuthorize("hasAuthority('access:manage')")
@Tag(name = "Roles and access")
public class AccessController {
    private final AccessService service;

    public AccessController(AccessService service) { this.service = service; }

    public record MatrixUpdate(@NotNull Set<String> permissions) {}
    public record ProviderChange(@NotNull String alias, boolean enabled) {}
    public record ProviderUpdate(@NotNull List<@Valid ProviderChange> providers) {}

    @GetMapping("/roles")
    public List<RoleSummary> roles() { return service.roles(); }

    public record CreateRoleRequest(@NotNull String name, String description) {}

    @PostMapping("/roles")
    public org.springframework.http.ResponseEntity<RoleSummary> createRole(CurrentUser me, @Valid @RequestBody CreateRoleRequest body) {
        return org.springframework.http.ResponseEntity.status(201).body(service.createRole(me, body.name(), body.description()));
    }

    @DeleteMapping("/roles/{role}")
    public org.springframework.http.ResponseEntity<Void> deleteRole(CurrentUser me, @PathVariable String role) {
        service.deleteRole(me, role);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/roles/{role}/matrix")
    public RoleMatrix matrix(@PathVariable String role) { return service.matrix(role); }

    @PutMapping("/roles/{role}/matrix")
    public RoleMatrix updateMatrix(CurrentUser me, @PathVariable String role, @Valid @RequestBody MatrixUpdate body) {
        return service.updateMatrix(me, role, body.permissions());
    }

    @GetMapping("/identity-providers")
    public List<IdentityProviderInfo> providers() { return service.identityProviders(); }

    @PutMapping("/identity-providers")
    public List<IdentityProviderInfo> updateProviders(CurrentUser me, @Valid @RequestBody ProviderUpdate body) {
        Map<String, Boolean> changes = new HashMap<>();
        body.providers().forEach(p -> changes.put(p.alias(), p.enabled()));
        return service.updateIdentityProviders(me, changes);
    }

    @GetMapping("/settings")
    public AccessSettings settings() { return service.settings(); }
}
