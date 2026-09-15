package com.rizenfood.api.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 포트원 웹훅 서명 검증 (Standard Webhooks 규격).
 *
 *   서명 대상  = "{webhook-id}.{webhook-timestamp}.{요청 본문 원본 바이트}"
 *   서명       = Base64( HMAC-SHA256( 키, 서명 대상 ) )
 *   헤더       = "v1,<서명> v1,<서명> ..."  (키 교체 중엔 여러 개 — 하나라도 맞으면 통과)
 *   키         = 시크릿 "whsec_<base64>" 의 base64 를 디코딩한 바이트
 *
 * 오래된 알림(±5분 밖)은 거절한다 — 가로챈 요청을 나중에 다시 보내는 공격을 막는다.
 * 비교는 상수 시간으로 한다 — 응답 시간 차이로 서명을 한 글자씩 맞혀 보는 공격을 막는다.
 *
 * ★ 본문은 반드시 받은 바이트 그대로 검증한다. JSON 을 파싱했다가 다시 직렬화하면 서명이 어긋난다.
 */
public final class PortOneWebhookVerifier {

    static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String SECRET_PREFIX = "whsec_";

    private final byte[] key;
    private final Clock clock;

    public PortOneWebhookVerifier(String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("웹훅 시크릿이 비어 있습니다.");
        }
        String s = secret.trim();
        this.key = s.startsWith(SECRET_PREFIX)
                ? Base64.getDecoder().decode(s.substring(SECRET_PREFIX.length()))
                : s.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public boolean verify(String webhookId, String timestamp, String signatureHeader, byte[] body) {
        if (isBlank(webhookId) || isBlank(timestamp) || isBlank(signatureHeader) || body == null) {
            return false;
        }
        Instant sentAt = parseTimestamp(timestamp.trim());
        if (sentAt == null) {
            return false;
        }
        Duration skew = Duration.between(sentAt, clock.instant()).abs();
        if (skew.compareTo(TOLERANCE) > 0) {
            return false;
        }

        byte[] expected = sign(webhookId.trim(), timestamp.trim(), body);
        for (String part : signatureHeader.trim().split("\\s+")) {
            int comma = part.indexOf(',');
            if (comma <= 0 || !"v1".equals(part.substring(0, comma))) {
                continue; // 모르는 버전은 건너뛴다
            }
            byte[] given = part.substring(comma + 1).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, given)) {
                return true;
            }
        }
        return false;
    }

    private byte[] sign(String id, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return Base64.getEncoder().encode(mac.doFinal());
        } catch (Exception e) {
            throw new IllegalStateException("웹훅 서명 계산 실패", e);
        }
    }

    /** 규격은 초 단위 정수. 포트원 문서가 RFC 3339 로도 설명하므로 둘 다 받는다. */
    private static Instant parseTimestamp(String value) {
        try {
            if (value.chars().allMatch(Character::isDigit)) {
                return Instant.ofEpochSecond(Long.parseLong(value));
            }
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
