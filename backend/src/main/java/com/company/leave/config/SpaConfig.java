package com.company.leave.config;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 프론트엔드(SPA) 정적 파일을 jar 내부(classpath:/static/)에서 서빙한다.
 * 존재하지 않는 경로(딥링크/새로고침)는 index.html 로 포워드하되, /api 는 컨트롤러가 처리하도록 비켜준다.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // API·문서·액추에이터는 정적 리졸버가 가로채지 않음
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("v3/api-docs")
                                || resourcePath.startsWith("swagger-ui")
                                || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        // 그 외 경로는 SPA 진입점으로
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
