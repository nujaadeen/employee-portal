package com.baracel.portal.ai.service;

import com.baracel.portal.access.service.AccessService;
import com.baracel.portal.access.service.PortalRoles;
import com.baracel.portal.ai.client.AiServiceClient;
import com.baracel.portal.ai.domain.KnowledgeActivity;
import com.baracel.portal.ai.domain.KnowledgeCollection;
import com.baracel.portal.ai.repo.KnowledgeActivityRepository;
import com.baracel.portal.ai.repo.KnowledgeCollectionRepository;
import com.baracel.portal.common.security.CurrentUser;
import com.baracel.portal.common.service.AuditService;
import com.baracel.portal.common.web.ApiException;
import com.baracel.portal.common.web.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * Knowledge base administration. Spring owns the catalogue of collections and who may use each (the permission
 * ai:knowledge:&lt;code&gt;, granted to roles in the access matrix). The AI service owns the documents, counts and indexing status.
 */
@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]{2,39}$");
    static final String PERM_PREFIX = "ai:knowledge:";

    public record CollectionAdmin(String code, String name, int documents, int chunks, String status, int failedDocuments,
                                  @Nullable Instant lastSync, List<String> sourceTypes, List<String> roles, boolean allEmployees) {}
    public record KnowledgeOverview(boolean aiServiceAvailable, List<CollectionAdmin> collections) {}
    public record JobAccepted(String jobId, @Nullable String documentId, String status) {}
    public record JobFailure(String sourceRef, String error) {}
    public record KnowledgeJob(String id, String collection, String kind, String status, int progress, @Nullable String error,
                               List<JobFailure> failures, Instant createdAt, @Nullable Instant finishedAt) {}
    public record KnowledgeDocument(String id, String sourceType, String sourceRef, String title, String status, @Nullable String error,
                                    int chunkCount, Instant updatedAt) {}
    public record ActivityItem(String jobId, String collection, String collectionName, String kind, String label, String startedBy,
                               Instant startedAt, String status, int progress, @Nullable String error) {}
    public record DriveSource(String folderId, @Nullable Boolean recursive, @Nullable Boolean force) {}
    public record WebSource(List<String> urls, @Nullable Boolean followLinks, @Nullable Integer maxPages, @Nullable Boolean force) {}

    private final KnowledgeCollectionRepository collections;
    private final KnowledgeActivityRepository activity;
    private final AccessService access;
    private final AuditService audit;
    private final ObjectProvider<AiServiceClient> aiProvider;
    private final TransactionTemplate tx;
    private final long maxUploadBytes;

    public KnowledgeService(KnowledgeCollectionRepository collections, KnowledgeActivityRepository activity, AccessService access,
                            AuditService audit, ObjectProvider<AiServiceClient> aiProvider, TransactionTemplate tx,
                            @Value("${portal.ai.service.max-upload-mb:25}") long maxUploadMb) {
        this.collections = collections;
        this.activity = activity;
        this.access = access;
        this.audit = audit;
        this.aiProvider = aiProvider;
        this.tx = tx;
        this.maxUploadBytes = maxUploadMb * 1024 * 1024;
    }

    private AiServiceClient ai() {
        AiServiceClient c = aiProvider.getIfAvailable();
        if (c == null) throw ApiException.unavailable("ai-unavailable", "The AI service is not enabled (portal.ai.mode is not http).");
        return c;
    }

    // ---- catalogue ----

    public KnowledgeOverview overview() {
        List<KnowledgeCollection> catalogue = tx.execute(st -> collections.findAllByOrderBySortOrderAsc());
        Map<String, JsonNode> remote = new HashMap<>();
        boolean available = true;
        AiServiceClient client = aiProvider.getIfAvailable();
        if (client == null) available = false;
        else try {
            for (JsonNode c : client.collections()) remote.put(c.path("code").asString(), c);
        } catch (ApiException e) {
            available = false;
        }
        List<CollectionAdmin> out = new ArrayList<>();
        for (KnowledgeCollection c : catalogue) {
            JsonNode r = remote.get(c.code);
            int failed = 0;
            List<String> types = new ArrayList<>();
            if (r != null && client != null) {
                try {
                    Set<String> seen = new TreeSet<>();
                    for (JsonNode d : client.documents(c.code)) {
                        seen.add(d.path("source_type").asString());
                        if ("FAILED".equals(d.path("status").asString())) failed++;
                    }
                    types.addAll(seen);
                } catch (ApiException ignored) { available = false; }
            }
            int docs = r != null ? r.path("documents").asInt() : (available ? 0 : c.docCount);
            if (r != null && r.path("documents").asInt() != c.docCount) setDocCount(c.code, docs);
            List<String> roles = access.rolesWithPermission(PERM_PREFIX + c.code);
            out.add(new CollectionAdmin(c.code, c.name, docs, r != null ? r.path("chunks").asInt() : 0,
                    r != null ? r.path("status").asString("READY") : (available ? "EMPTY" : "UNKNOWN"), failed,
                    r != null && r.hasNonNull("last_sync") ? Instant.parse(r.get("last_sync").asString()) : null, types, roles,
                    roles.contains(PortalRoles.EMPLOYEE)));
        }
        return new KnowledgeOverview(available, out);
    }

    public CollectionAdmin create(CurrentUser me, String code, String name) {
        if (code == null || !CODE.matcher(code).matches())
            throw ApiException.badRequest("invalid-code", "The code has 3 to 40 characters: lowercase letters, digits and hyphens, starting with a letter.");
        if (name == null || name.isBlank() || name.length() > 100) throw ApiException.badRequest("validation", "The name needs 1 to 100 characters.");
        tx.executeWithoutResult(st -> {
            if (collections.findAllByOrderBySortOrderAsc().stream().anyMatch(c -> c.code.equals(code)))
                throw ApiException.conflict("collection-exists", "A collection with the code " + code + " already exists.");
            KnowledgeCollection c = new KnowledgeCollection();
            c.code = code;
            c.name = name.trim();
            c.permission = PERM_PREFIX + code;
            c.sortOrder = collections.findAllByOrderBySortOrderAsc().stream().mapToInt(x -> x.sortOrder).max().orElse(0) + 1;
            collections.save(c);
            access.registerPermission("ai", "knowledge:" + code, c.name + " knowledge", "Search " + c.name.toLowerCase(), "AI assistant",
                    c.name + " knowledge", "VIEW");
            access.setRolesForPermission(c.permission, List.of(PortalRoles.ADMIN));
            audit.log(me, "KNOWLEDGE_COLLECTION_CREATED", "knowledge-collection", code, c.name);
        });
        return new CollectionAdmin(code, name.trim(), 0, 0, "EMPTY", 0, null, List.of(), List.of(PortalRoles.ADMIN), false);
    }

    public List<String> setRoles(CurrentUser me, String code, List<String> roles) {
        KnowledgeCollection c = require(code);
        Set<String> wanted = new LinkedHashSet<>(roles == null ? List.of() : roles);
        wanted.add(PortalRoles.ADMIN);   // ADMIN always keeps access, like access:manage
        List<String> before = access.rolesWithPermission(c.permission);
        access.setRolesForPermission(c.permission, wanted);   // validates the roles and clears the permission cache
        List<String> after = access.rolesWithPermission(c.permission);
        audit.log(me, "KNOWLEDGE_ROLES_UPDATED", "knowledge-collection", code, "before=" + before + " after=" + after);
        return after;
    }

    public void delete(CurrentUser me, String code) {
        KnowledgeCollection c = require(code);
        int documents = 0;
        for (JsonNode r : ai().collections()) if (code.equals(r.path("code").asString())) documents = r.path("documents").asInt();
        if (documents > 0)
            throw ApiException.conflict("collection-not-empty", "The collection still has " + documents + " documents. Delete them first.");
        tx.executeWithoutResult(st -> {
            access.removePermission(c.permission);
            collections.delete(c);
            audit.log(me, "KNOWLEDGE_COLLECTION_DELETED", "knowledge-collection", code, c.name);
        });
    }

    // ---- proxy to the AI service ----

    public JobAccepted upload(CurrentUser me, String code, MultipartFile file) {
        KnowledgeCollection c = require(code);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("validation", "Choose a PDF file.");
        if (file.getSize() > maxUploadBytes)
            throw new ApiException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, "file-too-large", "The file is larger than " + (maxUploadBytes / 1024 / 1024) + " MB.");
        String type = file.getContentType() == null ? "application/pdf" : file.getContentType();
        try (var in = file.getInputStream()) {
            JsonNode r = ai().uploadPdf(code, file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename(), type, in, file.getSize());
            return accepted(me, c, "PDF", file.getOriginalFilename(), r);
        } catch (IOException e) {
            throw ApiException.badRequest("validation", "The file could not be read.");
        }
    }

    public JobAccepted drive(CurrentUser me, String code, DriveSource body) {
        KnowledgeCollection c = require(code);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folder_id", body.folderId());
        m.put("recursive", body.recursive() == null || body.recursive());
        m.put("force", Boolean.TRUE.equals(body.force()));
        return accepted(me, c, "DRIVE", "Drive folder " + body.folderId(), ai().drive(code, m));
    }

    public JobAccepted web(CurrentUser me, String code, WebSource body) {
        KnowledgeCollection c = require(code);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("urls", body.urls());
        m.put("follow_links", Boolean.TRUE.equals(body.followLinks()));
        if (body.maxPages() != null) m.put("max_pages", body.maxPages());
        m.put("force", Boolean.TRUE.equals(body.force()));
        String host = body.urls() == null || body.urls().isEmpty() ? "website" : java.net.URI.create(body.urls().get(0)).getHost();
        return accepted(me, c, "WEB", host == null ? "website" : host, ai().web(code, m));
    }

    public JobAccepted reindex(CurrentUser me, String code) {
        KnowledgeCollection c = require(code);
        return accepted(me, c, "REINDEX", "Re-index " + c.name, ai().reindex(code));
    }

    public List<KnowledgeDocument> documents(String code) {
        require(code);
        List<KnowledgeDocument> out = new ArrayList<>();
        for (JsonNode d : ai().documents(code))
            out.add(new KnowledgeDocument(d.path("id").asString(), d.path("source_type").asString(), d.path("source_ref").asString(), d.path("title").asString(),
                    d.path("status").asString(), d.path("error").asString(null), d.path("chunk_count").asInt(), Instant.parse(d.path("updated_at").asString())));
        return out;
    }

    public void deleteDocument(CurrentUser me, String code, String documentId) {
        require(code);
        ai().deleteDocument(code, documentId);
        audit.log(me, "KNOWLEDGE_DOCUMENT_DELETED", "knowledge-collection", code, documentId);
        refreshCount(code);
    }

    /** Job status. A finished job brings knowledge_collection.doc_count in step with the AI service. */
    public KnowledgeJob job(String id) {
        JsonNode j = ai().job(id);
        KnowledgeJob job = toJob(j);
        if (List.of("DONE", "PARTIAL", "FAILED").contains(job.status())) refreshCount(job.collection());
        return job;
    }

    public List<ActivityItem> recentActivity() {
        List<KnowledgeActivity> rows = tx.execute(st -> activity.findTop20ByOrderByCreatedAtDesc());
        Map<String, String> names = new HashMap<>();
        tx.execute(st -> collections.findAllByOrderBySortOrderAsc()).forEach(c -> names.put(c.code, c.name));
        List<ActivityItem> out = new ArrayList<>();
        AiServiceClient client = aiProvider.getIfAvailable();
        int live = 0;
        for (KnowledgeActivity a : rows) {
            String status = "UNKNOWN";
            int progress = 0;
            String error = null;
            if (client != null && live++ < 10) {
                try {
                    KnowledgeJob j = toJob(client.job(a.jobId));
                    status = j.status();
                    progress = j.progress();
                    error = j.error();
                } catch (ApiException e) { /* the status stays UNKNOWN */ }
            }
            out.add(new ActivityItem(a.jobId, a.collection, names.getOrDefault(a.collection, a.collection), a.kind, a.label, a.userName, a.createdAt, status, progress, error));
        }
        return out;
    }

    // ---- helpers ----

    private JobAccepted accepted(CurrentUser me, KnowledgeCollection c, String kind, String label, JsonNode r) {
        String jobId = r.path("job_id").asString();
        tx.executeWithoutResult(st -> {
            if (activity.findByJobId(jobId).isEmpty()) {
                KnowledgeActivity a = new KnowledgeActivity();
                a.jobId = jobId;
                a.collection = c.code;
                a.kind = kind;
                a.label = label == null ? kind : (label.length() > 500 ? label.substring(0, 500) : label);
                a.userId = me.employeeId();
                a.userName = me.name();
                activity.save(a);
            }
            audit.log(me, "KNOWLEDGE_" + kind + "_STARTED", "knowledge-collection", c.code, label);
        });
        return new JobAccepted(jobId, r.path("document_id").asString(null), r.path("status").asString("QUEUED"));
    }

    private KnowledgeJob toJob(JsonNode j) {
        List<JobFailure> failures = new ArrayList<>();
        j.path("failures").forEach(f -> failures.add(new JobFailure(f.path("source_ref").asString(), f.path("error").asString())));
        return new KnowledgeJob(j.path("id").asString(), j.path("collection").asString(), j.path("kind").asString(), j.path("status").asString(),
                j.path("progress").asInt(), j.path("error").asString(null), failures, Instant.parse(j.path("created_at").asString()),
                j.hasNonNull("finished_at") ? Instant.parse(j.get("finished_at").asString()) : null);
    }

    private void refreshCount(String code) {
        try {
            for (JsonNode r : ai().collections()) if (code.equals(r.path("code").asString())) { setDocCount(code, r.path("documents").asInt()); return; }
            setDocCount(code, 0);   // the AI service no longer lists the collection: no documents
        } catch (ApiException e) {
            log.warn("Could not refresh the document count of {}: {}", code, e.getMessage());
        }
    }

    private void setDocCount(String code, int docs) {
        tx.executeWithoutResult(st -> collections.findAllByOrderBySortOrderAsc().stream().filter(c -> c.code.equals(code)).findFirst().ifPresent(c -> c.docCount = docs));
    }

    private KnowledgeCollection require(String code) {
        return tx.execute(st -> collections.findAllByOrderBySortOrderAsc().stream().filter(c -> c.code.equals(code)).findFirst())
                .orElseThrow(() -> ApiException.notFound("Unknown collection: " + code));
    }
}
