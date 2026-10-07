package com.rizenfood.api.notify;

import java.util.List;
import java.util.Map;

/**
 * 알림톡 템플릿 문구 (2026-10-06).
 *
 * ★ 카카오는 심사를 통과한 템플릿과 글자 하나까지 같은 문구만 보낸다. 여기 문구를 고치면
 *   카카오 심사를 다시 받아야 한다. 바꾸지 말고, 바꿀 일이 생기면 새 템플릿으로 신청한다.
 * ★ 알림톡은 정보성 메시지만 된다 — 할인·이벤트 같은 광고 문구를 넣으면 심사에서 떨어진다.
 *   효능·효과 표현도 넣지 않는다 (CLAUDE.md 규칙 1).
 * ★ #{…} 자리는 발송할 때 주문 값으로 채운다. 변수 이름도 심사받은 그대로여야 한다.
 *
 * 문구 상태: 초안 — 대표 승인 대기. 승인되면 이 문구 그대로 카카오에 신청한다.
 */
public enum AlimtalkTemplate {

    PAID("""
            #{고객명}님, 주문이 접수되었습니다.

            ■ 주문 정보
            주문번호: #{주문번호}
            상품명: #{상품명}
            결제금액: #{결제금액}원

            상품이 출고되면 택배사와 송장번호를 다시 안내해 드리겠습니다.""",
            List.of("고객명", "주문번호", "상품명", "결제금액")),

    SHIPPED("""
            #{고객명}님, 주문하신 상품이 발송되었습니다.

            ■ 주문 정보
            주문번호: #{주문번호}
            상품명: #{상품명}

            ■ 배송 정보
            택배사: #{택배사}
            송장번호: #{송장번호}""",
            List.of("고객명", "주문번호", "상품명", "택배사", "송장번호")),

    /** 취소·반품 승인 후 환불 완료 (2026-10-07 문구 승인). 취소와 반품 모두 이 한 템플릿으로 보낸다. */
    REFUNDED("""
            #{고객명}님, 요청하신 환불이 완료되었습니다.

            ■ 환불 정보
            주문번호: #{주문번호}
            상품명: #{상품명}
            환불금액: #{환불금액}원

            결제하신 수단으로 환불되며, 카드사에 따라 실제 반영까지 영업일 기준 3~7일이 걸릴 수 있습니다.""",
            List.of("고객명", "주문번호", "상품명", "환불금액"));

    /** 버튼 이름. 두 템플릿 모두 사이트 주문 조회로 보낸다(알림톡 버튼은 등록한 도메인만 열 수 있다). */
    public static final String BUTTON_NAME = "주문 조회";

    /** 버튼이 여는 사이트 안 경로. 비회원도 주문번호·연락처로 볼 수 있는 화면이다. */
    public static final String BUTTON_PATH = "/orders/lookup";

    private final String text;
    private final List<String> variables;

    AlimtalkTemplate(String text, List<String> variables) {
        this.text = text;
        this.variables = variables;
    }

    public String text() {
        return text;
    }

    public List<String> variables() {
        return variables;
    }

    /**
     * 변수를 채운 본문. 업체에 따라 본문 전체를 보내는 곳이 있다(템플릿과 대조해 발송).
     *
     * @throws IllegalArgumentException 템플릿에 있는 변수가 하나라도 비었을 때 — 빈칸 알림을 보내지 않는다
     */
    public String render(Map<String, String> values) {
        String out = text;
        for (String name : variables) {
            String v = values.get(name);
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("알림톡 변수 값이 없다: " + name);
            }
            out = out.replace("#{" + name + "}", v);
        }
        return out;
    }
}