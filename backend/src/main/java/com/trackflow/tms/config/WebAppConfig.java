package com.trackflow.tms.config;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React app from the backend itself (single-instance hosting, see
 * Dockerfile.render). Files under classpath:/static/ are returned as they are;
 * any other non-API path gets index.html so client-side routes such as
 * /tickets/42 survive a page reload. Off by default: in Kubernetes the SPA has
 * its own nginx pods.
 */
@Configuration
@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true")
public class WebAppConfig implements WebMvcConfigurer {

    private static final Resource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        return isApiPath(path) ? null : INDEX;
                    }
                });
    }

    static boolean isApiPath(String path) {
        return path.startsWith("api/") || path.startsWith("actuator/") || path.startsWith("v3/")
                || path.startsWith("swagger-ui");
    }
}
