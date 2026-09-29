package com.company.leave.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SESSION = "sessionCookie";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("연차관리 시스템 API")
                        .description("Annual Leave Management System REST API")
                        .version("v0.1.0"))
                // 인증: POST /api/auth/login 성공 시 발급되는 세션 쿠키(SESSION). 같은 출처에서 자동 전송.
                .addSecurityItem(new SecurityRequirement().addList(SESSION))
                .components(new Components().addSecuritySchemes(SESSION,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")));
    }
}
