package com.baracel.portal.gitea.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gitea")
public record GiteaProperties(String baseUrl, String publicUrl, String token, String webhookSecret, boolean moveToReviewOnPrOpen,
                              boolean moveToDoneOnPrMerge) {}
