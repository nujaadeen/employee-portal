package com.baracel.portal.identity.service;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CurrentUserArgumentResolver resolver;

    public WebConfig(CurrentUserArgumentResolver resolver) { this.resolver = resolver; }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) { resolvers.add(resolver); }
}
