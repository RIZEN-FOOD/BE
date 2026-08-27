package com.rizenfood.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 값은 application.yml 의 app.jwt.* 에 있다.
 *
 * secret 은 .env 로만 넣는다. 저장소에 들어가면 누구나 토큰을 위조할 수 있다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        /** 관리자 액세스 토큰 유효시간(분) */
        long adminExpiryMinutes,
        /** 쿠키에 Secure 를 붙일지. 운영은 반드시 true. */
        boolean secureCookie,
        /** 쿠키 SameSite 값 */
        String sameSite) {

    public JwtProperties {
        if (adminExpiryMinutes <= 0) {
            adminExpiryMinutes = 240; // 4시간
        }
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
    }
}
