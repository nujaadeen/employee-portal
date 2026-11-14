package com.baracel.portal;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baracel.portal.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/** Exports the API contract to docs/openapi.json. Run: mvn -q verify (the file is rewritten on every test run). */
class OpenApiExportIT extends IntegrationTest {
    @Test
    void exportContract() throws Exception {
        String json = mvc.perform(getAs("daniel.okoye", "/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ObjectMapper pretty = new ObjectMapper().rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
        Path target = Path.of("..", "docs", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, pretty.writeValueAsString(pretty.readTree(json)) + "\n");
    }
}
