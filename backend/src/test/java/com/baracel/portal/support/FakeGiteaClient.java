package com.baracel.portal.support;

import com.baracel.portal.gitea.service.GiteaClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClientException;

public class FakeGiteaClient implements GiteaClient {
    public final List<String> branches = new ArrayList<>();
    public boolean failBranch = false;

    @Override public RepoInfo repository(String fullName) {
        if (fullName.startsWith("missing/")) throw new RestClientException("404");
        return new RepoInfo(fullName, "main", "http://gitea.test/" + fullName);
    }
    @Override public void createBranch(String repo, String newBranch, String fromBranch) {
        if (failBranch) throw new RestClientException("gitea down");
        branches.add(repo + "#" + newBranch + "<-" + fromBranch);
    }
    @Override public List<RepoInfo> listRepositories() { return List.of(new RepoInfo("baracel/otp-service", "main", "http://gitea.test/baracel/otp-service")); }
    @Override public PullInfo pullRequest(String repo, int number) {
        return new PullInfo(number, "Linked PR " + number, "open", false, "feature/bar-146-webhook-retries", "abc123def4567890abc123def4567890abc123de",
                "http://gitea.test/" + repo + "/pulls/" + number);
    }
    @Override public String checks(String repo, String sha) { return "PASSING"; }

    public void reset() { branches.clear(); failBranch = false; }
}
