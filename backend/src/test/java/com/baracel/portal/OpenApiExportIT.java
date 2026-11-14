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

    /** Request bodies whose every field is optional. The JSON serializer drops an empty required list, so these are the only schemas without one. */
    private static final java.util.Set<String> ALL_OPTIONAL = java.util.Set.of("BranchRequest", "DecisionRequest", "NewConversation", "UpdateTaskRequest", "SubtaskUpdate", "UpdateEmployee");

    /** Contract rule: an object schema with properties always has a required list, unless every field is optional (listed above). */
    @Test
    void everyObjectSchemaDeclaresItsRequiredFields() throws Exception {
        String json = mvc.perform(getAs("daniel.okoye", "/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        tools.jackson.databind.JsonNode schemas = new ObjectMapper().readTree(json).path("components").path("schemas");
        java.util.List<String> problems = new java.util.ArrayList<>();
        int checked = 0;
        for (var e : schemas.properties()) {
            tools.jackson.databind.JsonNode s = e.getValue();
            if (!s.has("properties") || s.path("properties").isEmpty()) continue;
            checked++;
            boolean hasRequired = s.path("required").isArray() && s.path("required").size() > 0;
            if (ALL_OPTIONAL.contains(e.getKey())) { if (hasRequired) problems.add(e.getKey() + " should be all optional"); }
            else if (!hasRequired) problems.add(e.getKey());
        }
        org.junit.jupiter.api.Assertions.assertTrue(checked > 50, "expected many object schemas, saw " + checked);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(), problems, "object schemas without a required list");

        // spot checks: non-null components are required, nullable ones are optional and accept null
        tools.jackson.databind.JsonNode me = schemas.path("MeResponse");
        java.util.List<String> required = new java.util.ArrayList<>();
        me.path("required").forEach(n -> required.add(n.asText()));
        org.junit.jupiter.api.Assertions.assertTrue(required.containsAll(java.util.List.of("id", "name", "email", "status", "roles", "permissions", "signInMethods", "unreadNotifications")), required.toString());
        org.junit.jupiter.api.Assertions.assertFalse(required.contains("jobTitle"));
        org.junit.jupiter.api.Assertions.assertFalse(required.contains("manager"));
        org.junit.jupiter.api.Assertions.assertTrue(me.path("properties").path("jobTitle").path("type").toString().contains("null"), me.path("properties").path("jobTitle").toString());
        org.junit.jupiter.api.Assertions.assertEquals(0, schemas.path("UpdateEmployee").path("required").size());   // every field optional
    }
}
