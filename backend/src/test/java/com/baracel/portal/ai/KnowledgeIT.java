package com.baracel.portal.ai;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baracel.portal.ai.client.SimpleCircuitBreaker;
import com.baracel.portal.support.HttpModeIT;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class KnowledgeIT extends HttpModeIT {
    static final String COLLECTIONS = """
            [{"code":"product","documents":12,"chunks":40,"last_sync":"2026-11-15T09:12:00+00:00","status":"READY"},
             {"code":"brand","documents":3,"chunks":9,"last_sync":null,"status":"INDEXING"},
             {"code":"engineering","documents":2,"chunks":6,"last_sync":null,"status":"FAILED"}]""";
    static final String DOCS = """
            [{"id":"d1","collection":"product","source_type":"PDF","source_ref":"a.pdf","title":"a.pdf","status":"READY","error":null,"chunk_count":4,"updated_at":"2026-11-15T09:00:00+00:00"},
             {"id":"d2","collection":"product","source_type":"DRIVE","source_ref":"f2","title":"Broken","status":"FAILED","error":"The PDF could not be opened","chunk_count":0,"updated_at":"2026-11-15T09:00:00+00:00"}]""";

    @Autowired JdbcTemplate jdbc;
    @Autowired SimpleCircuitBreaker breaker;
    @PersistenceContext EntityManager em;

    @BeforeEach
    void closeBreaker() { breaker.success(); }

    @Test
    void everyKnowledgeEndpointIsForbiddenWithoutTheManagePermission() throws Exception {
        for (String user : new String[]{"aisha.rahman", "lena.fischer", "priya.nair", "omar.haddad"}) {
            mvc.perform(getAs(user, "/api/knowledge/collections")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/knowledge/activity")).andExpect(status().isForbidden());
            mvc.perform(postAs(user, "/api/knowledge/collections", "{\"code\":\"abc\",\"name\":\"Abc\"}")).andExpect(status().isForbidden());
            mvc.perform(putAs(user, "/api/knowledge/collections/product/roles", "{\"roles\":[\"SALES\"]}")).andExpect(status().isForbidden());
            mvc.perform(deleteAs(user, "/api/knowledge/collections/product")).andExpect(status().isForbidden());
            mvc.perform(MockMvcRequestBuilders.multipart("/api/knowledge/collections/product/documents")
                    .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes())).header("Authorization", as(user))).andExpect(status().isForbidden());
            mvc.perform(postAs(user, "/api/knowledge/collections/product/sources/drive", "{\"folderId\":\"abcde12345\"}")).andExpect(status().isForbidden());
            mvc.perform(postAs(user, "/api/knowledge/collections/product/sources/web", "{\"urls\":[\"https://baracel.example/\"]}")).andExpect(status().isForbidden());
            mvc.perform(postAs(user, "/api/knowledge/collections/product/reindex", "{}")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/knowledge/collections/product/documents")).andExpect(status().isForbidden());
            mvc.perform(deleteAs(user, "/api/knowledge/collections/product/documents/d1")).andExpect(status().isForbidden());
            mvc.perform(getAs(user, "/api/knowledge/jobs/j1")).andExpect(status().isForbidden());
        }
        WM.verify(0, anyRequestedFor(urlPathMatching("/v1/.*")));   // nothing reached the AI service
        mvc.perform(get401()).andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get401() { return MockMvcRequestBuilders.get("/api/knowledge/collections"); }

    @Test
    void theOverviewMergesTheCatalogueTheRolesAndTheAiServiceCounts() throws Exception {
        WM.stubFor(get("/v1/collections").willReturn(okJson(COLLECTIONS)));
        WM.stubFor(get("/v1/collections/product/documents").willReturn(okJson(DOCS)));
        WM.stubFor(get(urlPathMatching("/v1/collections/(brand|engineering)/documents")).willReturn(okJson("[]")));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/collections")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiServiceAvailable").value(true))
                .andExpect(jsonPath("$.collections", hasSize(5)))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].documents").value(12))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].status").value("READY"))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].failedDocuments").value(1))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].sourceTypes[*]").value(contains("DRIVE", "PDF")))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].roles[*]").value(contains("SALES", "ADMIN")))
                .andExpect(jsonPath("$.collections[?(@.code=='brand')].status").value("INDEXING"))
                .andExpect(jsonPath("$.collections[?(@.code=='hr-policies')].roles[*]").value(contains("HR", "ADMIN")))
                .andExpect(jsonPath("$.collections[?(@.code=='hr-policies')].status").value("EMPTY"))      // the AI service has no documents for it
                .andExpect(jsonPath("$.collections[?(@.code=='engineering')].roles[*]").value(contains("ENGINEER", "ADMIN")));
        WM.verify(getRequestedFor(urlEqualTo("/v1/collections")).withHeader("Authorization", equalTo("Bearer svc-ingest-token")));   // management uses ai:ingest
        em.flush();
        assertEquals(12, jdbc.queryForObject("select doc_count from knowledge_collection where code = 'product'", Integer.class));   // kept in step
    }

    @Test
    void whenTheAiServiceIsDownTheCatalogueStillShowsWithTheLastKnownCounts() throws Exception {
        WM.stubFor(get("/v1/collections").willReturn(serverError()));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/collections")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aiServiceAvailable").value(false))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].documents").value(214))
                .andExpect(jsonPath("$.collections[?(@.code=='product')].status").value("UNKNOWN"));
    }

    @Test
    void createSetRolesAndDeleteACollection() throws Exception {
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections", "{\"code\":\"partner-faq\",\"name\":\"Partner FAQ\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("partner-faq")).andExpect(jsonPath("$.roles", contains("ADMIN")));
        for (String bad : new String[]{"ab", "Partner", "1abc", "has_underscore", "x".repeat(41)})
            mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections", "{\"code\":\"" + bad + "\",\"name\":\"Bad\"}")).andExpect(status().isBadRequest());
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections", "{\"code\":\"partner-faq\",\"name\":\"Again\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", endsWith("/collection-exists")));
        // Mei (EMPLOYEE) cannot search it yet
        mvc.perform(getAs("mei.tan", "/api/ai/access")).andExpect(jsonPath("$.collections[?(@.code=='partner-faq')].allowed").value(false));
        // the role list is replaced, ADMIN always stays, and the permission cache is cleared at once
        mvc.perform(putAs("daniel.okoye", "/api/knowledge/collections/partner-faq/roles", "{\"roles\":[\"EMPLOYEE\",\"SALES\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("EMPLOYEE", "SALES", "ADMIN")));
        mvc.perform(getAs("mei.tan", "/api/ai/access")).andExpect(jsonPath("$.collections[?(@.code=='partner-faq')].allowed").value(true));
        mvc.perform(putAs("daniel.okoye", "/api/knowledge/collections/partner-faq/roles", "{\"roles\":[\"SALES\"]}")).andExpect(jsonPath("$.roles", contains("SALES", "ADMIN")));
        mvc.perform(getAs("mei.tan", "/api/ai/access")).andExpect(jsonPath("$.collections[?(@.code=='partner-faq')].allowed").value(false));
        mvc.perform(putAs("daniel.okoye", "/api/knowledge/collections/partner-faq/roles", "{\"roles\":[\"NOT_A_ROLE\"]}")).andExpect(status().isNotFound());
        mvc.perform(putAs("daniel.okoye", "/api/knowledge/collections/nope/roles", "{\"roles\":[\"SALES\"]}")).andExpect(status().isNotFound());
        em.flush();
        assertEquals(3, jdbc.queryForObject("select count(*) from audit_log where action in ('KNOWLEDGE_COLLECTION_CREATED','KNOWLEDGE_ROLES_UPDATED')", Integer.class));
        // it shows up in the roles and access matrix as a permission
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/SALES/matrix")).andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:partner-faq')].granted").value(true));
        // delete: refused while the AI service still holds documents, allowed when it holds none
        WM.stubFor(get("/v1/collections").willReturn(okJson("[{\"code\":\"partner-faq\",\"documents\":3,\"chunks\":9,\"last_sync\":null,\"status\":\"READY\"}]")));
        mvc.perform(deleteAs("daniel.okoye", "/api/knowledge/collections/partner-faq")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", endsWith("/collection-not-empty")));
        WM.stubFor(get("/v1/collections").willReturn(okJson("[]")));
        mvc.perform(deleteAs("daniel.okoye", "/api/knowledge/collections/partner-faq")).andExpect(status().isNoContent());
        mvc.perform(getAs("daniel.okoye", "/api/access/roles/SALES/matrix")).andExpect(jsonPath("$.permissions[?(@.key=='ai:knowledge:partner-faq')]", hasSize(0)));
        assertEquals(0, jdbc.queryForObject("select count(*) from knowledge_collection where code = 'partner-faq'", Integer.class));
    }

    @Test
    void anUploadIsStreamedThroughWithAnIngestTokenAndLoggedAsActivity() throws Exception {
        WM.stubFor(post("/v1/collections/product/documents").willReturn(aResponse().withStatus(202).withHeader("Content-Type", "application/json")
                .withBody("{\"job_id\":\"job-1\",\"document_id\":\"doc-1\",\"status\":\"QUEUED\"}")));
        byte[] pdf = ("%PDF-1.4\n" + "x".repeat(50_000)).getBytes();
        mvc.perform(MockMvcRequestBuilders.multipart("/api/knowledge/collections/product/documents")
                        .file(new MockMultipartFile("file", "Merchant FAQ.pdf", "application/pdf", pdf)).header("Authorization", as("daniel.okoye")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value("job-1")).andExpect(jsonPath("$.documentId").value("doc-1"));
        WM.verify(postRequestedFor(urlEqualTo("/v1/collections/product/documents"))
                .withHeader("Authorization", equalTo("Bearer svc-ingest-token"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBody(containing("filename=\"Merchant FAQ.pdf\"")).withRequestBody(containing("%PDF-1.4")));
        WM.stubFor(get("/v1/jobs/job-1").willReturn(okJson("{\"id\":\"job-1\",\"collection\":\"product\",\"kind\":\"PDF\",\"status\":\"INDEXING\",\"progress\":64,\"error\":null,\"failures\":[],\"created_at\":\"2026-11-15T09:00:00+00:00\",\"finished_at\":null}")));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/activity")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Merchant FAQ.pdf")).andExpect(jsonPath("$[0].startedBy").value("Daniel Okoye"))
                .andExpect(jsonPath("$[0].collectionName").value("Product catalogue")).andExpect(jsonPath("$[0].status").value("INDEXING"))
                .andExpect(jsonPath("$[0].progress").value(64));
    }

    @Test
    void theSizeLimitIsEnforcedHereBeforeAnythingIsSent() throws Exception {
        byte[] big = new byte[1024 * 1024 + 100];   // the limit is 1 MB in tests
        System.arraycopy("%PDF-".getBytes(), 0, big, 0, 5);
        mvc.perform(MockMvcRequestBuilders.multipart("/api/knowledge/collections/product/documents")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", big)).header("Authorization", as("daniel.okoye")))
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.type", endsWith("/file-too-large")));
        WM.verify(0, postRequestedFor(urlEqualTo("/v1/collections/product/documents")));
    }

    @Test
    void aFinishedJobBringsTheDocumentCountInStepWithTheAiService() throws Exception {
        WM.stubFor(get("/v1/jobs/job-9").willReturn(okJson("{\"id\":\"job-9\",\"collection\":\"brand\",\"kind\":\"PDF\",\"status\":\"DONE\",\"progress\":100,\"error\":null,\"failures\":[],\"created_at\":\"2026-11-15T09:00:00+00:00\",\"finished_at\":\"2026-11-15T09:01:00+00:00\"}")));
        WM.stubFor(get("/v1/collections").willReturn(okJson("[{\"code\":\"brand\",\"documents\":7,\"chunks\":20,\"last_sync\":null,\"status\":\"READY\"}]")));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/jobs/job-9")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.progress").value(100));
        em.flush();
        assertEquals(7, jdbc.queryForObject("select doc_count from knowledge_collection where code = 'brand'", Integer.class));
        // a running job does not touch the count
        WM.stubFor(get("/v1/jobs/job-10").willReturn(okJson("{\"id\":\"job-10\",\"collection\":\"campaign\",\"kind\":\"DRIVE\",\"status\":\"RUNNING\",\"progress\":30,\"error\":null,\"failures\":[],\"created_at\":\"2026-11-15T09:00:00+00:00\",\"finished_at\":null}")));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/jobs/job-10")).andExpect(jsonPath("$.status").value("RUNNING"));
        em.flush();
        assertEquals(96, jdbc.queryForObject("select doc_count from knowledge_collection where code = 'campaign'", Integer.class));
    }

    @Test
    void driveWebReindexDocumentsAndDeleteAreProxiedAndAiServiceErrorsPassThrough() throws Exception {
        WM.stubFor(post(urlPathMatching("/v1/collections/product/(sources/drive|sources/web|reindex)")).willReturn(aResponse().withStatus(202).withHeader("Content-Type", "application/json").withBody("{\"job_id\":\"j2\",\"document_id\":null,\"status\":\"QUEUED\"}")));
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections/product/sources/drive", "{\"folderId\":\"1AbCdEfGh\",\"recursive\":false}")).andExpect(status().isAccepted());
        WM.verify(postRequestedFor(urlEqualTo("/v1/collections/product/sources/drive")).withRequestBody(matchingJsonPath("$.folder_id", equalTo("1AbCdEfGh")))
                .withRequestBody(matchingJsonPath("$.recursive", equalTo("false"))).withHeader("Authorization", equalTo("Bearer svc-ingest-token")));
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections/product/sources/web", "{\"urls\":[\"https://baracel.example/\"],\"followLinks\":true,\"maxPages\":5}")).andExpect(status().isAccepted());
        WM.verify(postRequestedFor(urlEqualTo("/v1/collections/product/sources/web")).withRequestBody(matchingJsonPath("$.follow_links", equalTo("true"))).withRequestBody(matchingJsonPath("$.max_pages", equalTo("5"))));
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections/product/reindex", "{}")).andExpect(status().isAccepted());
        WM.stubFor(get("/v1/collections/product/documents").willReturn(okJson(DOCS)));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/collections/product/documents")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].status").value("FAILED")).andExpect(jsonPath("$[1].error").value("The PDF could not be opened")).andExpect(jsonPath("$[0].chunkCount").value(4));
        WM.stubFor(delete("/v1/collections/product/documents/d1").willReturn(aResponse().withStatus(204)));
        WM.stubFor(get("/v1/collections").willReturn(okJson("[]")));
        mvc.perform(deleteAs("daniel.okoye", "/api/knowledge/collections/product/documents/d1")).andExpect(status().isNoContent());
        // the AI service says no (422): the caller sees that status and the reason, not a 500
        WM.stubFor(post("/v1/collections/brand/sources/web").willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/problem+json")
                .withBody("{\"type\":\"https://baracel.example/problems/different-domain\",\"detail\":\"All URLs must be on the same domain\",\"status\":422}")));
        mvc.perform(postAs("daniel.okoye", "/api/knowledge/collections/brand/sources/web", "{\"urls\":[\"https://a.example/\",\"https://b.example/\"]}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.type", endsWith("/different-domain"))).andExpect(jsonPath("$.detail").value("All URLs must be on the same domain"));
        mvc.perform(getAs("daniel.okoye", "/api/knowledge/collections/unknown/documents")).andExpect(status().isNotFound());
        assertNotNull(JsonPath.parse("{}"));
    }
}
