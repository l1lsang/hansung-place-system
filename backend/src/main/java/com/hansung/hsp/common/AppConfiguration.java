package com.hansung.hsp.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class AppConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("HSP API").version("1")
                .description("세션 인증. GET /api/auth/csrf 후 변경 요청에 X-XSRF-TOKEN을 전송합니다. "
                        + "로그인 후 CSRF 토큰을 다시 조회합니다. 시간은 UTC offset이 있는 ISO-8601 형식입니다."))
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")));
    }
}

