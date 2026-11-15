package com.baracel.portal.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.baracel.portal.ai.service.AiAssistantPort.*;
import com.baracel.portal.ai.service.StubAiAssistant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StubAiAssistantTest {
    private final StubAiAssistant stub = new StubAiAssistant();
    private static final List<String> ALL = List.of("product", "brand", "campaign", "hr-policies", "engineering");

    private AiReply ask(String message, List<String> allowed) {
        return stub.chat(new AiRequest(UUID.randomUUID(), message, List.of(), new AiUser(UUID.randomUUID(), "T", List.of("X")), allowed));
    }

    @Test
    void adAndPostMessagesReturnTheSampleDraft() {
        for (String m : new String[]{"Write an ad", "make a POST for linkedin", "draft an advert", "Instagram ads please"}) {
            AiReply r = ask(m, List.of("product", "brand"));
            assertNotNull(r.draft(), m);
            assertEquals("SOCIAL_POST", r.draft().kind());
            assertEquals("Scan. Pay. Done.", r.draft().headline());
            assertEquals(List.of("#Baracel", "#QRPayments", "#SmallBusiness"), r.draft().hashtags());
        }
        assertNull(ask("What is the weather like?", List.of("product", "brand")).draft());
        assertNull(ask("The postal service is slow", List.of("product", "brand")).draft());   // "postal" is not "post"
    }

    @Test
    void sourcesAlwaysComeFromAllowedCollectionsForEverySubsetOfCollections() {
        for (int mask = 0; mask < (1 << ALL.size()); mask++) {
            List<String> allowed = new java.util.ArrayList<>();
            for (int i = 0; i < ALL.size(); i++) if ((mask & (1 << i)) != 0) allowed.add(ALL.get(i));
            for (String message : new String[]{"Write an ad for our product", "Tell me about leave", "hello"}) {
                AiReply r = ask(message, allowed);
                for (AiSource s : r.sources())
                    assertTrue(allowed.contains(s.collection()), "source " + s + " leaked for allowed " + allowed + " and message " + message);
            }
        }
    }

    @Test
    void noAllowedCollectionsMeansNoSourcesAndNoDraft() {
        AiReply r = ask("Write an ad post", List.of());
        assertTrue(r.sources().isEmpty());
        assertNull(r.draft());
        assertTrue(ask("hello", List.of()).sources().isEmpty());
    }

    @Test
    void draftNeedsProductOrBrandKnowledge() {
        assertNull(ask("write an ad", List.of("engineering", "hr-policies")).draft());
        AiReply onlyBrand = ask("write an ad", List.of("brand"));
        assertNotNull(onlyBrand.draft());
        assertEquals(List.of("brand"), onlyBrand.sources().stream().map(AiSource::collection).toList());
    }
}
