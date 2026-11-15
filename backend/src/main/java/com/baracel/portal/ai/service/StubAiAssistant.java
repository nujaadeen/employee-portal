package com.baracel.portal.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Phase 1 stand-in. Returns the sample draft for ads and posts, otherwise a short canned answer. */
@Component
@ConditionalOnProperty(name = "portal.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiAssistant implements AiAssistantPort {
    private static final Pattern AD_OR_POST = Pattern.compile("\\b(ads?|adverts?|advertisements?|posts?)\\b", Pattern.CASE_INSENSITIVE);

    /** One sample document per collection. A source is only ever picked from collections in allowedCollections. */
    private static final Map<String, String> SAMPLE_DOCS = Map.of(
            "product", "Product brief v3.pdf",
            "brand", "Brand voice guide",
            "campaign", "Campaign archive: QR launch",
            "engineering", "Engineering handbook",
            "hr-policies", "Leave policy 2026");

    @Override
    public AiReply chat(AiRequest req) {
        boolean wantsPost = AD_OR_POST.matcher(req.userMessage()).find();
        List<String> allowed = req.allowedCollections();
        if (wantsPost) {
            boolean product = allowed.contains("product"), brand = allowed.contains("brand");
            if (!product && !brand) {
                return new AiReply("I can only draft marketing copy from product and brand knowledge, and your role does not include either of those collections.",
                        List.of(), null);
            }
            List<AiSource> sources = new ArrayList<>();
            if (product) sources.add(new AiSource(SAMPLE_DOCS.get("product"), "product"));
            if (brand) sources.add(new AiSource(SAMPLE_DOCS.get("brand"), "brand"));
            String text = "Here is a draft" + (brand ? " that follows the brand voice guide (plain, warm, no jargon)." : ".")
                    + (product ? " I used the product brief for the claims." : "")
                    + " Pick where to publish, or ask me to change the tone.";
            return new AiReply(text, sources, new AiDraftReply("SOCIAL_POST", "Scan. Pay. Done.",
                    "Your customers already scan to pay. Now your counter can too. Set up Baracel QR in minutes and see every payment in one place.",
                    List.of("#Baracel", "#QRPayments", "#SmallBusiness"), List.of("company-instagram", "company-linkedin")));
        }
        List<AiSource> sources = allowed.stream().filter(SAMPLE_DOCS::containsKey).limit(2)
                .map(c -> new AiSource(SAMPLE_DOCS.get(c), c)).toList();
        String text = "This is a Phase 1 stub answer. " + (sources.isEmpty()
                ? "Your role does not give access to any knowledge collection yet."
                : "In Phase 2 the assistant will search " + String.join(", ", allowed) + " to answer this.");
        return new AiReply(text, sources, null);
    }
}
