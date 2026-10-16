package com.rizenfood.api.notify;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자(대표) 알림 문자 (2026-10-16).
 *
 * 새 주문(결제 완료)·취소·반품·교환 요청이 들어오면 운영자 휴대폰으로 문자를 보낸다.
 * 알림톡과 달리 카카오 템플릿 검수가 필요 없어 바로 켤 수 있다(솔라피 문자, 발신번호는 알림톡과 같다).
 *
 * @param enabled 켜기 (기본 꺼짐)
 * @param phones  받을 번호. 쉼표로 여러 개 (예: 01012345678,01098765432)
 */
@ConfigurationProperties(prefix = "app.admin-notify")
public record AdminNotifyProperties(boolean enabled, String phones) {

    public AdminNotifyProperties {
        phones = phones == null ? "" : phones;
    }

    /** 숫자만 남긴 번호 목록 (10자리 미만은 버린다). */
    public List<String> phoneList() {
        return Arrays.stream(phones.split(","))
                .map(p -> p.replaceAll("\\D", ""))
                .filter(p -> p.length() >= 10)
                .distinct()
                .toList();
    }
}
