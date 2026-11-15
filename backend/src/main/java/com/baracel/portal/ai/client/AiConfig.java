package com.baracel.portal.ai.client;

import com.baracel.portal.ai.service.AiAssistantPort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** portal.ai.mode=http switches from the Phase 1 stub to the AI service. */
@Configuration
@EnableConfigurationProperties({AiProperties.class, com.baracel.portal.ai.social.SocialProperties.class})
public class AiConfig {
    @Bean
    @ConditionalOnProperty(name = "portal.ai.mode", havingValue = "http")
    AiServiceTokens aiServiceTokens(AiProperties props) { return new AiServiceTokens(props.service()); }

    @Bean
    @ConditionalOnProperty(name = "portal.ai.mode", havingValue = "http")
    SimpleCircuitBreaker aiCircuitBreaker(AiProperties props, Clock clock) {
        return new SimpleCircuitBreaker(props.breaker().failureThreshold(), props.breaker().openFor(), clock);
    }

    @Bean
    @ConditionalOnProperty(name = "portal.ai.mode", havingValue = "http")
    AiServiceClient aiServiceClient(AiProperties props, AiServiceTokens tokens, SimpleCircuitBreaker breaker) {
        return new AiServiceClient(props, tokens, breaker);
    }

    @Bean
    @ConditionalOnProperty(name = "portal.ai.mode", havingValue = "http")
    AiAssistantPort httpAiAssistant(AiServiceClient client, ObjectMapper mapper) { return new HttpAiAssistantClient(client, mapper); }
}
