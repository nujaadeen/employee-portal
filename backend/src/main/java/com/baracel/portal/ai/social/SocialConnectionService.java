package com.baracel.portal.ai.social;

import com.baracel.portal.ai.domain.SocialConnection;
import com.baracel.portal.ai.domain.SocialOAuthState;
import com.baracel.portal.ai.repo.SocialConnectionRepository;
import com.baracel.portal.ai.repo.SocialOAuthStateRepository;
import com.baracel.portal.ai.social.LinkedInClient.*;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.common.web.Nullable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LinkedIn accounts linked to the portal: personal (one per person) and the company page. Tokens are encrypted at rest and never leave
 * this class in clear text except to call LinkedIn. Access tokens are refreshed when LinkedIn issued a refresh token; when that fails
 * (or there is none and the token expired) the connection becomes EXPIRED and the person reconnects.
 */
@Service
public class SocialConnectionService {
    private static final Logger log = LoggerFactory.getLogger(SocialConnectionService.class);
    static final List<String> PERSONAL_SCOPES = List.of("openid", "profile", "w_member_social");
    static final List<String> COMPANY_SCOPES = List.of("openid", "profile", "r_organization_admin", "w_organization_social");
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record ConnectionDto(UUID id, String provider, String type, String displayName, String status, @Nullable Instant expiresAt,
                                @Nullable String profileUrl, boolean canDisconnect, boolean canReconnect) {}
    public record ConnectStart(String authorizationUrl, String state) {}
    public record CallbackResult(String target, String result, @Nullable String reason) {}

    private final SocialConnectionRepository connections;
    private final SocialOAuthStateRepository states;
    private final LinkedInClient linkedin;
    private final TokenCipher cipher;
    private final SocialProperties props;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SocialConnectionService(SocialConnectionRepository connections, SocialOAuthStateRepository states, LinkedInClient linkedin,
                                   TokenCipher cipher, SocialProperties props, AuditService audit, TransactionTemplate tx, Clock clock) {
        this.connections = connections;
        this.states = states;
        this.linkedin = linkedin;
        this.cipher = cipher;
        this.props = props;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    public boolean linkedinEnabled() { return props.linkedin().enabled(); }
    public boolean companyEnabled() { return props.linkedin().enabled() && props.linkedin().companyEnabled(); }

    // ---- listing ----

    /** The caller's own connections plus the company connection when they may publish to company accounts. No secrets. */
    public List<ConnectionDto> list(CurrentUser me) {
        return tx.execute(st -> {
            List<ConnectionDto> out = new ArrayList<>();
            connections.findByOwnerIdAndStatusNot(me.employeeId(), "REVOKED").forEach(c -> out.add(dto(c, me)));
            if (me.has("social:publish:company") || me.has("access:manage"))
                connections.findByOwnerIdIsNullAndStatusNot("REVOKED").forEach(c -> out.add(dto(c, me)));
            return out;
        });
    }

    private ConnectionDto dto(SocialConnection c, CurrentUser me) {
        boolean company = c.ownerId == null;
        boolean canManage = company ? me.has("access:manage") : me.employeeId().equals(c.ownerId);
        String url = company && c.accountUrn.startsWith("urn:li:organization:")
                ? "https://www.linkedin.com/company/" + c.accountUrn.substring(c.accountUrn.lastIndexOf(':') + 1) : null;
        return new ConnectionDto(c.id, c.provider, company ? "COMPANY" : "PERSONAL", c.displayName, c.status, c.expiresAt, url, canManage, canManage);
    }

    // ---- connect ----

    public ConnectStart start(CurrentUser me, String target) {
        boolean company = "company".equalsIgnoreCase(target);
        if (!company && !"personal".equalsIgnoreCase(target)) throw ApiException.badRequest("validation", "target must be personal or company");
        if (!props.linkedin().enabled()) throw ApiException.conflict("linkedin-disabled", "LinkedIn publishing is switched off.");
        if (company) {
            if (!props.linkedin().companyEnabled()) throw ApiException.conflict("linkedin-company-disabled", "Company page publishing is not enabled yet.");
            if (!me.has("access:manage")) throw ApiException.forbidden("Only an administrator can connect the company page.");
        } else if (!me.has("social:publish:personal")) {
            throw ApiException.forbidden("Your role cannot publish to personal accounts.");
        }
        if (!cipher.configured()) throw ApiException.unavailable("social-not-configured", "Social token encryption is not configured (SOCIAL_TOKEN_KEY).");
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tx.executeWithoutResult(st -> {
            SocialOAuthState s = new SocialOAuthState();
            s.state = state;
            s.userId = me.employeeId();
            s.purpose = company ? "LINKEDIN_COMPANY" : "LINKEDIN_PERSONAL";
            s.createdAt = clock.instant();
            s.expiresAt = clock.instant().plus(STATE_TTL);
            states.save(s);
        });
        return new ConnectStart(linkedin.authorizationUrl(state, company ? COMPANY_SCOPES : PERSONAL_SCOPES), state);
    }

    /** The browser comes back from LinkedIn. The one-time state identifies the person; a wrong, replayed or expired state is refused. */
    public CallbackResult callback(String code, String state, String error) {
        SocialOAuthState s = state == null ? null : tx.execute(st -> claimState(state));
        if (s == null) return new CallbackResult("personal", "error", "invalid-state");
        String target = "LINKEDIN_COMPANY".equals(s.purpose) ? "company" : "personal";
        if (error != null && !error.isBlank()) return new CallbackResult(target, "cancelled", null);
        if (code == null || code.isBlank()) return new CallbackResult(target, "error", "no-code");
        try {
            TokenResponse t = linkedin.exchangeCode(code);
            if ("company".equals(target)) saveCompany(s.userId, t); else savePersonal(s.userId, t);
            return new CallbackResult(target, "connected", null);
        } catch (NoOrganization e) {
            return new CallbackResult(target, "error", "no-organization");
        } catch (LinkedInException | IllegalStateException e) {
            log.warn("LinkedIn connect failed: {}", e.getClass().getSimpleName());
            return new CallbackResult(target, "error", "linkedin-error");
        }
    }

    /** Single use: marks the state used, and returns it only if it was unused and not expired. */
    private SocialOAuthState claimState(String state) {
        SocialOAuthState s = states.findById(state).orElse(null);
        if (s == null || s.usedAt != null || !s.expiresAt.isAfter(clock.instant())) return null;
        s.usedAt = clock.instant();
        return s;
    }

    private void savePersonal(UUID userId, TokenResponse t) {
        Profile p = linkedin.profile(t.accessToken());
        tx.executeWithoutResult(st -> {
            SocialConnection c = connections.findFirstByOwnerIdAndProviderAndStatusNot(userId, "LINKEDIN", "REVOKED").orElseGet(SocialConnection::new);
            fill(c, userId, "urn:li:person:" + p.id(), p.name(), t, PERSONAL_SCOPES, userId);
            connections.save(c);
            audit.log(null, "SOCIAL_CONNECTED", "social-connection", c.id.toString(), "LINKEDIN personal, user " + userId);
        });
    }

    private void saveCompany(UUID userId, TokenResponse t) {
        List<Organization> orgs = linkedin.administeredOrganizations(t.accessToken());
        String wanted = props.linkedin().companyOrganizationId();
        Organization org = orgs.stream().filter(o -> wanted.isBlank() || o.urn().endsWith(":" + wanted)).findFirst().orElseThrow(NoOrganization::new);
        tx.executeWithoutResult(st -> {
            SocialConnection c = connections.findFirstByOwnerIdIsNullAndProviderAndAccountUrnAndStatusNot("LINKEDIN", org.urn(), "REVOKED").orElseGet(SocialConnection::new);
            fill(c, null, org.urn(), org.name(), t, COMPANY_SCOPES, userId);
            connections.save(c);
            audit.log(null, "SOCIAL_CONNECTED", "social-connection", c.id.toString(), "LINKEDIN company " + org.urn() + ", by user " + userId);
        });
    }

    private void fill(SocialConnection c, UUID owner, String urn, String name, TokenResponse t, List<String> scopes, UUID by) {
        if (c.id == null) { c.id = UUID.randomUUID(); c.createdAt = clock.instant(); }
        c.ownerId = owner;
        c.provider = "LINKEDIN";
        c.accountUrn = urn;
        c.displayName = name;
        c.accessTokenEnc = cipher.encrypt(t.accessToken());
        c.refreshTokenEnc = t.refreshToken() == null ? null : cipher.encrypt(t.refreshToken());
        c.expiresAt = t.expiresInSeconds() > 0 ? clock.instant().plusSeconds(t.expiresInSeconds()) : null;
        c.refreshExpiresAt = t.refreshExpiresInSeconds() == null ? null : clock.instant().plusSeconds(t.refreshExpiresInSeconds());
        c.scopes = t.scope() == null || t.scope().isBlank() ? String.join(" ", scopes) : t.scope().replace(',', ' ');
        c.status = "CONNECTED";
        c.createdBy = by;
        c.updatedAt = clock.instant();
    }

    private static class NoOrganization extends RuntimeException {}

    // ---- disconnect ----

    /** Own connections, and the company one only with access:manage. Someone else's connection is simply not found. */
    public void disconnect(CurrentUser me, UUID id) {
        tx.executeWithoutResult(st -> {
            SocialConnection c = connections.findById(id).filter(x -> !"REVOKED".equals(x.status)).orElseThrow(() -> ApiException.notFound("Connection not found"));
            boolean company = c.ownerId == null;
            if (!company && !me.employeeId().equals(c.ownerId)) throw ApiException.notFound("Connection not found");
            if (company && !me.has("access:manage")) throw ApiException.forbidden("Only an administrator can disconnect the company page.");
            c.status = "REVOKED";
            c.accessTokenEnc = "";
            c.refreshTokenEnc = null;
            c.updatedAt = clock.instant();
            audit.log(me, "SOCIAL_DISCONNECTED", "social-connection", id.toString(), c.provider + (company ? " company" : " personal"));
        });
    }

    // ---- using a connection ----

    /** The connection a person publishes through: their own personal one, or the company one. Null when there is none. */
    public SocialConnection find(UUID ownerOrNull, String provider) {
        return tx.execute(st -> (ownerOrNull == null
                ? connections.findFirstByOwnerIdIsNullAndProviderAndStatusNot(provider, "REVOKED")
                : connections.findFirstByOwnerIdAndProviderAndStatusNot(ownerOrNull, provider, "REVOKED")).orElse(null));
    }

    /**
     * A usable access token. Refreshes first when it expires within a minute and LinkedIn gave us a refresh token. When the token is
     * expired and cannot be refreshed the connection is marked EXPIRED and TokenExpired is thrown.
     */
    public String accessToken(UUID connectionId) {
        SocialConnection c = tx.execute(st -> connections.findById(connectionId).orElse(null));
        if (c == null || !"CONNECTED".equals(c.status)) throw new TokenExpired();
        Instant now = clock.instant();
        boolean expiring = c.expiresAt != null && !c.expiresAt.isAfter(now.plusSeconds(60));
        if (!expiring) return cipher.decrypt(c.accessTokenEnc);
        if (c.refreshTokenEnc == null) { markExpired(connectionId); throw new TokenExpired(); }
        try {
            TokenResponse t = linkedin.refresh(cipher.decrypt(c.refreshTokenEnc));
            tx.executeWithoutResult(st -> connections.findById(connectionId).ifPresent(x -> {
                x.accessTokenEnc = cipher.encrypt(t.accessToken());
                if (t.refreshToken() != null) x.refreshTokenEnc = cipher.encrypt(t.refreshToken());
                x.expiresAt = t.expiresInSeconds() > 0 ? clock.instant().plusSeconds(t.expiresInSeconds()) : null;
                x.updatedAt = clock.instant();
            }));
            return t.accessToken();
        } catch (LinkedInException | IllegalStateException e) {
            log.warn("Refreshing a LinkedIn token failed: {}", e.getClass().getSimpleName());
            markExpired(connectionId);
            throw new TokenExpired();
        }
    }

    public void markExpired(UUID connectionId) {
        tx.executeWithoutResult(st -> connections.findById(connectionId).ifPresent(x -> {
            x.status = "EXPIRED";
            x.updatedAt = clock.instant();
        }));
    }

    public static class TokenExpired extends RuntimeException {
        public TokenExpired() { super("The LinkedIn connection has expired. Reconnect it."); }
    }

    /** Daily: refresh tokens that expire within a week, and mark the ones that already ran out. */
    @Scheduled(cron = "0 30 3 * * *")
    public void sweep() {
        if (!props.linkedin().enabled()) return;
        List<UUID> due = tx.execute(st -> connections.findAll().stream()
                .filter(c -> "CONNECTED".equals(c.status) && c.expiresAt != null && c.expiresAt.isBefore(clock.instant().plus(Duration.ofDays(7)))).map(c -> c.id).toList());
        for (UUID id : due) {
            try { accessTokenForSweep(id); } catch (TokenExpired e) { log.info("LinkedIn connection {} expired", id); }
        }
    }

    private void accessTokenForSweep(UUID id) {
        SocialConnection c = tx.execute(st -> connections.findById(id).orElse(null));
        if (c == null) return;
        if (c.expiresAt != null && !c.expiresAt.isAfter(clock.instant()) && c.refreshTokenEnc == null) { markExpired(id); throw new TokenExpired(); }
        if (c.refreshTokenEnc != null) {
            try {
                TokenResponse t = linkedin.refresh(cipher.decrypt(c.refreshTokenEnc));
                tx.executeWithoutResult(st -> connections.findById(id).ifPresent(x -> {
                    x.accessTokenEnc = cipher.encrypt(t.accessToken());
                    if (t.refreshToken() != null) x.refreshTokenEnc = cipher.encrypt(t.refreshToken());
                    x.expiresAt = t.expiresInSeconds() > 0 ? clock.instant().plusSeconds(t.expiresInSeconds()) : null;
                    x.updatedAt = clock.instant();
                }));
            } catch (LinkedInException | IllegalStateException e) {
                markExpired(id);
                throw new TokenExpired();
            }
        }
    }

    public String frontendRedirect(CallbackResult r) {
        String base = props.frontendUrl().replaceAll("/+$", "") + "/accounts?linkedin=" + r.result() + "&target=" + r.target();
        return r.reason() == null ? base : base + "&reason=" + r.reason();
    }
}
