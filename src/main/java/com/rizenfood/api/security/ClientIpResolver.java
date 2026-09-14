package com.rizenfood.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 실제 접속자 IP 를 구한다. 로그인 기록·관리자 감사 로그·요청 횟수 제한이 모두 이 값을 쓴다.
 *
 * ★ X-Forwarded-For 를 그대로 믿지 않는다. 누구나 요청에 넣어 보낼 수 있는 헤더라
 *   믿으면 기록에 가짜 IP 가 남고, IP 기준 횟수 제한도 헤더만 바꿔 우회된다.
 *
 * 운영(app.client-ip.header=CF-Connecting-IP)
 *   API 서버는 Cloudflare Tunnel 뒤에 있어 인터넷에 열린 포트가 없다. 모든 요청이 Cloudflare 를
 *   거치고, CF-Connecting-IP 는 Cloudflare 가 채운다(클라이언트가 보낸 값은 덮어쓴다).
 *   프론트(Workers)가 같은 도메인(zone)의 API 로 넘기는 요청에도 원 접속자 IP 가 담긴다.
 *
 * 로컬(값 비움)
 *   연결 주소(getRemoteAddr)를 쓴다. 헤더를 믿을 근거가 없는 환경이기 때문이다.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_LENGTH = 64;

    private final String trustedHeader;

    public ClientIpResolver(@Value("${app.client-ip.header:}") String trustedHeader) {
        this.trustedHeader = trustedHeader == null ? "" : trustedHeader.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (!trustedHeader.isEmpty()) {
            String value = request.getHeader(trustedHeader);
            if (value != null && !value.isBlank()) {
                String first = value.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return truncate(first);
                }
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }
}
