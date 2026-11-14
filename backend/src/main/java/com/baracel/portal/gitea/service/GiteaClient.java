package com.baracel.portal.gitea.service;

import java.util.List;

/** Port to the Gitea REST API. A fake implementation is used in tests. */
public interface GiteaClient {
    record RepoInfo(String fullName, String defaultBranch, String htmlUrl) {}
    record PullInfo(int number, String title, String state, boolean merged, String headRef, String headSha, String htmlUrl) {}

    RepoInfo repository(String fullName);
    void createBranch(String repo, String newBranch, String fromBranch);
    List<RepoInfo> listRepositories();
    PullInfo pullRequest(String repo, int number);
    /** PASSING, FAILING or PENDING; null when there is no status for the commit. */
    String checks(String repo, String sha);
}
