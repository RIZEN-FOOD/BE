package com.rizenfood.api.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 알림톡 설정 (2026-10-06).
 *
 * 알림톡은 카카오 공식 딜러사(발송 대행사)를 거쳐서만 보낼 수 있다. 업체가 정해지면 그 업체용
 * 발송기(AlimtalkSender 구현)를 붙이고 provider 를 바꾼다. 키는 서버 환경변수로만 넣는다.
 *
 * ★ 기본은 꺼짐. 꺼져 있으면 아무것도 보내지 않는다(provider=none 이면 로그만 남긴다).
 * ★ 템플릿 코드는 카카오 심사를 통과한 뒤 업체 콘솔에서 받는다. 문구는 AlimtalkTemplate 과 글자 하나까지 같아야 한다.
 *
 * @param enabled         켜기
 * @param provider        none(보내지 않음) | 업체 이름(업체가 정해지면 추가)
 * @param templatePaid    «결제 완료» 템플릿 코드
 * @param templateShipped «상품 출고 안내» 템플릿 코드
 */
@ConfigurationProperties(prefix = "app.alimtalk")
public record AlimtalkProperties(
        boolean enabled,
        String provider,
        String templatePaid,
        String templateShipped) {

    public AlimtalkProperties {
        provider = provider == null || provider.isBlank() ? "none" : provider.trim().toLowerCase();
        templatePaid = templatePaid == null ? "" : templatePaid.trim();
        templateShipped = templateShipped == null ? "" : templateShipped.trim();
    }

    /** 이 알림의 템플릿 코드. 비어 있으면 그 알림은 보내지 않는다. */
    public String templateCode(AlimtalkTemplate template) {
        return switch (template) {
            case PAID -> templatePaid;
            case SHIPPED -> templateShipped;
        };
    }
}