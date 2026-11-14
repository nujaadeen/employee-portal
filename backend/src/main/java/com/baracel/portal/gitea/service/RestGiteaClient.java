package com.baracel.portal.gitea.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestGiteaClient implements GiteaClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public RestGiteaClient(GiteaProperties props) {
        this.http = RestClient.builder().baseUrl(props.baseUrl() + "/api/v1")
                .defaultHeader("Authorization", "token " + props.token()).build();
    }

    @Override
    public RepoInfo repository(String fullName) {
        Map<String, Object> m = http.get().uri("/repos/" + fullName).retrieve().body(MAP);
        return repo(m);
    }

    @Override
    public void createBranch(String repo, String newBranch, String fromBranch) {
        http.post().uri("/repos/" + repo + "/branches").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("new_branch_name", newBranch, "old_branch_name", fromBranch)).retrieve().toBodilessEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RepoInfo> listRepositories() {
        Map<String, Object> res = http.get().uri("/repos/search?limit=50").retrieve().body(MAP);
        List<RepoInfo> out = new ArrayList<>();
        for (Object o : (List<Object>) res.getOrDefault("data", List.of())) out.add(repo((Map<String, Object>) o));
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PullInfo pullRequest(String repo, int number) {
        Map<String, Object> m = http.get().uri("/repos/" + repo + "/pulls/" + number).retrieve().body(MAP);
        Map<String, Object> head = (Map<String, Object>) m.get("head");
        return new PullInfo(number, (String) m.get("title"), (String) m.get("state"), Boolean.TRUE.equals(m.get("merged")),
                (String) head.get("ref"), (String) head.get("sha"), (String) m.get("html_url"));
    }

    @Override
    public String checks(String repo, String sha) {
        Map<String, Object> m = http.get().uri("/repos/" + repo + "/commits/" + sha + "/status").retrieve().body(MAP);
        Object state = m.get("state");
        return state == null ? null : GiteaWebhookService.mapChecks(String.valueOf(state));
    }

    private static RepoInfo repo(Map<String, Object> m) {
        return new RepoInfo((String) m.get("full_name"), (String) m.getOrDefault("default_branch", "main"), (String) m.get("html_url"));
    }
}
