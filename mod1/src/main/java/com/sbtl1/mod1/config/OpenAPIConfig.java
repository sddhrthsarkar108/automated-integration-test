package com.sbtl1.mod1.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    @Bean
    public OpenAPI defineOpenAPI() {
        Contact contact = new Contact().name("xxx xxx").email("xxx@xxx.xxx");
        Info info = new Info()
                .title("Mod1 API")
                .version("1.0")
                .description("This APIs exposes endpoints of mod1.")
                .contact(contact);
        return new OpenAPI().info(info);
    }
}
