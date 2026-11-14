package com.baracel.portal.unit;

import static org.junit.jupiter.api.Assertions.*;

import com.baracel.portal.gitea.service.GiteaService;
import com.baracel.portal.gitea.service.GiteaWebhookService;
import java.util.List;
import org.junit.jupiter.api.Test;

class GiteaHelpersTest {
    @Test
    void taskKeysAreFoundInBranchesTitlesAndMessages() {
        assertEquals(List.of("BAR-142"), List.copyOf(GiteaWebhookService.keys("feature/bar-142-whatsapp-otp")));
        assertEquals(List.of("BAR-146", "BAR-151"), List.copyOf(GiteaWebhookService.keys("Fix BAR-146 and bar-151", null)));
        assertEquals(List.of("BAR-142"), List.copyOf(GiteaWebhookService.keys("BAR-142: add client", "again BAR-142")));
        assertTrue(GiteaWebhookService.keys("release-1", "no key here", "X-1", "TOOLONGKEY-12", "BAR-").isEmpty());
        assertEquals(List.of("AB-1", "ABCDEF-22"), List.copyOf(GiteaWebhookService.keys("AB-1 ABCDEF-22")));
    }

    @Test
    void checksStatesAreMapped() {
        assertEquals("PASSING", GiteaWebhookService.mapChecks("success"));
        assertEquals("FAILING", GiteaWebhookService.mapChecks("failure"));
        assertEquals("FAILING", GiteaWebhookService.mapChecks("error"));
        assertEquals("PENDING", GiteaWebhookService.mapChecks("pending"));
    }

    @Test
    void branchNamesUseTheLowerCaseKeyAndASlug() {
        assertEquals("feature/bar-142-whatsapp-otp-delivery", GiteaService.branchName("BAR-142", "WhatsApp OTP delivery"));
        assertEquals("feature/bar-9-cafe-menu-1", GiteaService.branchName("BAR-9", "  Café menu #1!  "));
        assertEquals("feature/bar-7", GiteaService.branchName("BAR-7", "???"));
        String longOne = GiteaService.branchName("BAR-1", "a".repeat(100));
        assertTrue(longOne.length() <= "feature/bar-1-".length() + 40);
    }
}
