package com.sbtl1.mod1.config;

import com.sbtl1.mod1.util.JavaParserCodeFlowAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    @Bean
    @Primary
    public JavaParserCodeFlowAnalyzer javaParserCodeFlowAnalyzer() {
        return new JavaParserCodeFlowAnalyzer();
    }
} 