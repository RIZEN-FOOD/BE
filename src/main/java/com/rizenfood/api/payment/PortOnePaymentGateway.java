package com.rizenfood.api.payment;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 포트원(PortOne V2) 결제 어댑터.
 *
 * 흐름
 *   1) 브라우저가 포트원 결제창을 연다. paymentId 는 우리 주문번호다(추측 불가능한 값).
 *   2) 결제가 끝나면 브라우저가 /api/orders/{주문번호}/pay 를 부른다.
 *   3) 여기 approve() 가 포트원 API 로 그 결제를 직접 조회해, 상태가 PAID 이고
 *      금액이 서버 계산 금액과 같을 때만 확정한다(대조는 OrderService).
 *
 * ★ 브라우저가 "결제됐다"고 알려와도 믿지 않는다. 항상 서버가 포트원에 다시 묻는다.
 * ★ API 시크릿은 서버에만 둔다(.env 의 PORTONE_API_SECRET).
 *
 * app.payment.provider=portone 일 때만 등록된다. 그 외엔 MockPaymentGateway 가 쓰인다.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "portone")
public class PortOnePaymentGateway implements PaymentGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PortOnePaymentGateway.class);

    /** 키가 없으면 null — 결제를 받지 않는 상태다. */
    private final RestClient client;

    /**
     * ★ 키가 없어도 서버는 뜬다. PG 계약 전에도 사이트(상품·회원·관리자)를 먼저 올릴 수 있게 하기 위해서다.
     *   대신 결제·환불 요청은 전부 "결제 준비 중"으로 거절한다(fail closed).
     *   모의 결제처럼 돈 없이 결제 완료가 되는 일은 없다 — 운영에서 모의 결제는 아예 로드되지 않는다.
     *   결제수단별 채널 키도 비어 있으면 결제 화면이 결제 버튼을 막는다(PaymentConfigController).
     */
    public PortOnePaymentGateway(@Value("${app.payment.portone.api-secret:}") String apiSecret,
                                 @Value("${app.payment.portone.api-base:https://api.portone.io}") String apiBase) {
        if (apiSecret == null || apiSecret.isBlank()) {
            log.warn("PORTONE_API_SECRET 이 비어 있다 — 결제를 받지 않는 상태로 시작한다 (결제 요청은 모두 거절).");
            this.client = null;
            return;
        }
        this.client = RestClient.builder()
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
    }

    /** 결제를 받을 수 있는 상태인가 (키가 설정됨). */
    public boolean configured() {
        return client != null;
    }

    private RestClient client() {
        if (client == null) {
            throw new PaymentUnavailableException("결제 준비 중입니다. 잠시 후 다시 이용해 주세요.");
        }
        return client;
    }

    @Override
    public String provider() {
        return "PORTONE";
    }

    @Override
    public Approval approve(String orderNo, int expectedAmount) {
        JsonNode payment = fetch(orderNo);
        String status = payment.path("status").asText("");
        if (!"PAID".equals(status)) {
            throw new PaymentException("결제가 완료되지 않았습니다. (상태: " + status + ")");
        }
        if (!"KRW".equals(payment.path("currency").asText("KRW"))) {
            throw new PaymentException("원화 결제만 지원합니다.");
        }
        int approvedAmount = payment.path("amount").path("total").asInt(-1);
        String tid = firstNonBlank(payment.path("transactionId").asText(""), payment.path("id").asText(""));
        String receiptUrl = payment.path("receiptUrl").asText(null);
        return new Approval(tid, approvedAmount, methodLabel(payment.path("method")), receiptUrl);
    }

    @Override
    public void cancel(String orderNo, Integer amount, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason == null || reason.isBlank() ? "주문 취소" : reason);
        if (amount != null) {
            body.put("amount", amount); // 없으면 전액 취소
        }
        try {
            client().post().uri("/payments/{paymentId}/cancel", orderNo)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new PaymentException("결제 취소(환불)에 실패했습니다. (" + e.getStatusCode().value() + ")");
        }
    }

    /** 포트원 결제 단건 조회. paymentId 는 경로 변수로 넘겨 자동 인코딩된다. */
    private JsonNode fetch(String paymentId) {
        try {
            JsonNode node = client().get().uri("/payments/{paymentId}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new PaymentException("결제 정보를 받지 못했습니다.");
            }
            return node;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PaymentException("결제 내역을 찾을 수 없습니다.");
            }
            throw new PaymentException("결제 확인 중 오류가 발생했습니다. (" + e.getStatusCode().value() + ")");
        }
    }

    /** 포트원 결제수단 타입을 화면·관리자에 보일 한글 이름으로. */
    private static String methodLabel(JsonNode method) {
        String type = method.path("type").asText("");
        return switch (type) {
            case "PaymentMethodCard" -> "신용·체크카드";
            case "PaymentMethodTransfer" -> "계좌이체";
            case "PaymentMethodVirtualAccount" -> "가상계좌";
            case "PaymentMethodMobile" -> "휴대폰";
            case "PaymentMethodEasyPay" -> switch (method.path("provider").asText("")) {
                case "KAKAOPAY" -> "카카오페이";
                case "NAVERPAY" -> "네이버페이";
                case "TOSSPAY" -> "토스페이";
                default -> "간편결제";
            };
            default -> "기타";
        };
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
