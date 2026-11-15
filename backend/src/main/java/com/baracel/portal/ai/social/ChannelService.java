package com.baracel.portal.ai.social;

import com.baracel.portal.ai.domain.SocialChannel;
import com.baracel.portal.ai.domain.SocialConnection;
import com.baracel.portal.ai.repo.SocialChannelRepository;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.web.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** The publish channels a person sees (the AI panel and the publish picker) and whether each can be used right now. */
@Service
public class ChannelService {
    public static final String COMING_LATER = "COMING_LATER", CONNECTED = "CONNECTED", SIMULATED = "SIMULATED",
            NOT_CONNECTED = "NOT_CONNECTED", EXPIRED = "EXPIRED", NOT_AVAILABLE = "NOT_AVAILABLE";

    public record ChannelDto(String code, String name, String type, String provider, boolean connected, boolean canPublish, String status,
                             @Nullable String accountName) {}

    private final SocialChannelRepository channels;
    private final SocialConnectionService connections;
    private final TransactionTemplate tx;

    public ChannelService(SocialChannelRepository channels, SocialConnectionService connections, TransactionTemplate tx) {
        this.channels = channels;
        this.connections = connections;
        this.tx = tx;
    }

    public List<ChannelDto> forUser(CurrentUser me) {
        List<SocialChannel> rows = tx.execute(st -> {
            if (me.has("social:publish:personal") && channels.findAllByOrderByIdAsc().stream()
                    .noneMatch(c -> "LINKEDIN".equals(c.provider) && me.employeeId().equals(c.ownerId))) {
                SocialChannel c = new SocialChannel();
                c.code = "personal-linkedin-" + me.employeeId().toString().substring(0, 8);
                c.name = "My LinkedIn (personal)";
                c.type = "PERSONAL";
                c.provider = "LINKEDIN";
                c.connected = false;
                c.ownerId = me.employeeId();
                channels.save(c);
            }
            return channels.findAllByOrderByIdAsc();
        });
        List<ChannelDto> out = new ArrayList<>();
        for (SocialChannel c : rows) {
            if ("PERSONAL".equals(c.type) && !me.employeeId().equals(c.ownerId)) continue;
            out.add(dto(me, c));
        }
        return out;
    }

    public ChannelDto dto(CurrentUser me, SocialChannel c) {
        boolean company = "COMPANY".equals(c.type);
        boolean permitted = company ? me.has("social:publish:company") : me.has("social:publish:personal") && me.employeeId().equals(c.ownerId);
        String status;
        String account = null;
        if (!"LINKEDIN".equals(c.provider)) {
            status = COMING_LATER;
        } else if (!connections.linkedinEnabled()) {
            status = c.connected ? SIMULATED : NOT_CONNECTED;   // local development: the stored flag decides
        } else if (company && !connections.companyEnabled()) {
            status = NOT_AVAILABLE;
        } else {
            SocialConnection conn = connections.find(company ? null : c.ownerId, "LINKEDIN");
            status = conn == null ? NOT_CONNECTED : "EXPIRED".equals(conn.status) ? EXPIRED : CONNECTED;
            if (conn != null) account = conn.displayName;
        }
        boolean usable = CONNECTED.equals(status) || SIMULATED.equals(status);
        return new ChannelDto(c.code, c.name, c.type, c.provider, usable, permitted && usable, status, account);
    }
}
