package com.aitravel.companion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfig {
}
