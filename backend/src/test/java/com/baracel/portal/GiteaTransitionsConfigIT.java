package com.baracel.portal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** The two automatic status moves can be switched off. */
@TestPropertySource(properties = {"gitea.move-to-review-on-pr-open=false", "gitea.move-to-done-on-pr-merge=false"})
class GiteaTransitionsConfigIT extends IntegrationTest {
    @Test
    void transitionsCanBeDisabled() throws Exception {
        String open = GiteaIT.pr("opened", 99, "feature/bar-142-hotfix", "Hotfix", false, "aaa");
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", "pull_request").header("X-Gitea-Signature", GiteaIT.sign(open))
                .contentType(MediaType.APPLICATION_JSON).content(open)).andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-142")).andExpect(jsonPath("$.task.task.status").value("IN_PROGRESS"));

        String merged = GiteaIT.pr("closed", 54, "feature/bar-138-static-qr", "Static QR code generation", true, "bbb");
        mvc.perform(post("/api/integrations/gitea/webhook").header("X-Gitea-Event", "pull_request").header("X-Gitea-Signature", GiteaIT.sign(merged))
                .contentType(MediaType.APPLICATION_JSON).content(merged)).andExpect(status().isOk());
        mvc.perform(getAs("aisha.rahman", "/api/tasks/BAR-138")).andExpect(jsonPath("$.task.task.status").value("IN_REVIEW"));
    }
}
