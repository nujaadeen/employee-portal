package com.baracel.portal.ai.social;

import com.baracel.portal.ai.social.SocialConnectionService.ConnectStart;
import com.baracel.portal.ai.social.SocialConnectionService.ConnectionDto;
import com.baracel.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/social")
@Tag(name = "Social accounts")
public class SocialController {
    private final SocialConnectionService service;

    public SocialController(SocialConnectionService service) { this.service = service; }

    @GetMapping("/connections")
    public List<ConnectionDto> connections(CurrentUser me) { return service.list(me); }

    @PostMapping("/linkedin/connect")
    public ConnectStart connect(CurrentUser me, @RequestParam(defaultValue = "personal") String target) { return service.start(me, target); }

    @DeleteMapping("/connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(CurrentUser me, @PathVariable UUID id) { service.disconnect(me, id); }

    /** LinkedIn sends the browser here. No bearer token: the one-time state identifies the person. Always redirects to the frontend. */
    @GetMapping("/linkedin/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String to = service.frontendRedirect(service.callback(code, state, error));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, to).build();
    }
}
