package com.company.leave.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 데이터를 바꾸는 API 뒤에 실시간 신호. 로그인·로그아웃·비밀번호 찾기는 다른 사람 화면과 무관해 뺀다. */
@Configuration
public class RealtimeConfig implements WebMvcConfigurer {

    private final RealtimeHub hub;

    public RealtimeConfig(RealtimeHub hub) {
        this.hub = hub;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RealtimeChangeInterceptor(hub))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/realtime");
    }
}
