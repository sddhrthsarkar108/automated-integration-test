package com.sbtl1.mod1.config;

import com.sbtl1.mod1.util.SimpleCodeFlowAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public SimpleCodeFlowAnalyzer simpleCodeFlowAnalyzer() {
        return new SimpleCodeFlowAnalyzer();
    }
} 