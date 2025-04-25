package com.aasx.transformer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@SuppressWarnings("null") CorsRegistry registry) {
                // 스프링 MVC 레벨에서 엔드포인트("/api/transformer/**")에 대해 
                // CORS 정책을 전역으로 설정
                registry.addMapping("/api/transformer/**")
                        // 클라이언트가 http://localhost:3000 혹은 http://localhost:3001 에서 보낸 요청만 허용
                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
                        // 위 Origin에서 들어오는 GET/POST/PUT/DELETE 는 물론, 브라우저의 pre‑flight 요청인 OPTIONS 도 허용
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        // 어떤 헤더든 전송 가능하도록 허용
                        .allowedHeaders("*")
                        // 응답에 Content‑Disposition 헤더가 포함되어 있으면, 브라우저에서 자바스크립트로도 읽을 수 있게 노출
                        // 다운로드 파일명 추출 시 필요
                        .exposedHeaders("Content-Disposition")
                        // 쿠키나 인증 정보를 함께 보내도록 허용
                        // ⭕ Access-Control-Allow-Credentials: true 가 응답 헤더에 추가
                        .allowCredentials(true);

            }
        };
    }
}
