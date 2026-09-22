package com.rizenfood.api.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.rizenfood.api.order.OrderService;

/**
 * 나이스페이 결과 통보(웹훅).
 *
 * 결제창에서 돌아오는 길({@link NicePayReturnController})이 끊겼을 때를 대비한 안전망이다.
 * 손님이 결제 직후 브라우저를 닫거나 통신이 끊기면 그 길로는 결과가 오지 않는데,
 * 웹훅은 서버끼리 오므로 도착한다. 돈은 빠졌는데 주문이 결제 대기로 남는 일을 막는다.
 *
 * ★ 응답은 반드시 <b>HTTP 200 + 본문 "OK" + Content-Type: text/html</b> 이어야 한다.
 *   나이스는 본문에 "OK" 가 없으면 실패로 보고 계속 재전송한다.
 * ★ 웹훅 내용을 그대로 믿지 않는다. 서명을 검증하고, 확정은 승인 API 를 다시 조회하는
 *   기존 경로(settleByWebhook)로 한다 — 금액 대조도 거기서 한 번 더 한다.
 * ★ 로그인 없이 열려 있는 주소다. 신뢰의 근거는 위변조 서명뿐이다.
 */
@RestController
@RequestMapping("/api/payment/webhook")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "nicepay")
public class NicePayWebhookController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(NicePayWebhookController.class);

    /** 나이스가 성공으로 인정하는 응답. 이 글자가 없으면 재전송이 계속된다. */
    private static final String ACK = "OK";

    private final NicePayGateway gateway;
    private final OrderService orderService;

    public NicePayWebhookController(NicePayGateway gateway, OrderService orderService) {
        this.gateway = gateway;
        this.orderService = orderService;
    }

    @PostMapping(value = "/nicepay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> onEvent(@RequestBody JsonNode body) {
        String orderId = body.path("orderId").asText("");
        String tid = body.path("tid").asText("");
        String status = body.path("status").asText("");
        String amount = body.path("amount").asText("");
        String ediDate = body.path("ediDate").asText("");
        String signature = body.path("signature").asText("");

        if (orderId.isBlank() || tid.isBlank()) {
            // 나이스 콘솔의 "테스트 호출"은 빈 값으로 올 수 있다. 재전송을 부르지 않게 OK 로 받는다.
            log.info("나이스 웹훅: 주문번호·거래키 없음 (테스트 호출로 본다)");
            return ack();
        }

        if (!gateway.verifyResultSignature(tid, amount, ediDate, signature)) {
            // 서명이 맞지 않으면 우리가 아는 거래가 아니다. 아무것도 하지 않는다.
            // 그래도 OK 로 받는다 — 재전송을 받아봐야 결과가 같고, 로그만 쌓인다.
            //
            // ★ 무엇이 어긋났는지 남긴다. 서명은 해시라 앞 8자만 적어도 같은지 다른지 판단된다
            //   (시크릿 키는 절대 남기지 않는다). 나이스 콘솔의 테스트 전문은 서명이 비어 오기도 한다.
            log.error("나이스 웹훅 서명 불일치 — 처리하지 않는다: order={} tid={} amount='{}' ediDate='{}' "
                            + "받은서명={} 계산한서명={}",
                    orderId, tid, amount, ediDate,
                    head(signature), head(gateway.expectedResultSignature(tid, amount, ediDate)));
            return ack();
        }

        switch (status) {
            case "paid" -> {
                // 돌아오는 길이 끊겼을 수 있다. 확정은 승인 API 를 다시 조회하는 경로로 한다.
                OrderService.WebhookSettle result = orderService.settleByWebhook(orderId);
                log.info("나이스 웹훅 결제완료: order={} tid={} 결과={}", orderId, tid, result);
            }
            case "cancelled", "partialCancelled" -> {
                // 나이스 관리자 화면에서 직접 취소한 경우를 잡는다. 우리 기록에 없으면 사람이 봐야 한다.
                if (!orderService.isRecordedAsCancelled(orderId)) {
                    log.warn("나이스에서 취소됐는데 우리 기록에는 없다 — 관리자 확인 필요: order={} tid={} 상태={}",
                            orderId, tid, status);
                }
            }
            default -> log.info("나이스 웹훅: order={} 상태={} (처리 대상 아님)", orderId, status);
        }
        return ack();
    }

    /** 해시 앞부분만. 같은지 다른지 보기에 충분하고, 전체를 남길 이유가 없다. */
    private static String head(String hash) {
        if (hash == null || hash.isBlank()) {
            return "(없음)";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8) + "…";
    }

    /** 나이스가 요구하는 형식 — text/html 로 "OK". */
    private ResponseEntity<String> ack() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(ACK);
    }
}
