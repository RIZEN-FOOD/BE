package com.rizenfood.api.payment;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizenfood.api.order.OrderService;

/**
 * 포트원 웹훅 수신.
 *
 * 왜 필요한가
 *   결제는 끝났는데 브라우저가 서버에 "결제 확정" 요청을 못 보내는 경우가 있다(창 닫힘, 네트워크 끊김,
 *   모바일 앱 전환). 웹훅이 없으면 30분 자동 정리가 뒤늦게 잡는다. 웹훅이 있으면 즉시 확정된다.
 *
 * ★ 웹훅 내용을 믿지 않는다. 서명을 검증한 뒤에도 "이 주문을 다시 확인하라"는 신호로만 쓰고,
 *   실제 확정은 포트원 API 로 결제를 다시 조회해 상태·금액을 대조한 뒤에만 한다(OrderService).
 * ★ 같은 알림이 여러 번 와도 안전하다 — 이미 처리된 주문은 건드리지 않는다.
 *
 * 응답: 200 처리(또는 무시), 401 서명 불일치, 500 아직 확정 못 함 → 포트원이 최대 5회 재전송한다.
 */
@RestController
@RequestMapping("/api/payment/webhook")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "portone")
public class PortOneWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PortOneWebhookController.class);

    private final PortOneWebhookVerifier verifier;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PortOneWebhookController(@Value("${app.payment.portone.webhook-secret:}") String secret,
                                    OrderService orderService, ObjectMapper objectMapper) {
        this.verifier = secret == null || secret.isBlank() ? null : new PortOneWebhookVerifier(secret, Clock.systemUTC());
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        if (verifier == null) {
            log.warn("PORTONE_WEBHOOK_SECRET 이 비어 있어 포트원 웹훅을 받지 않는다. (결제는 되지만 즉시 확정 보조가 꺼진다)");
        }
    }

    @PostMapping("/portone")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        if (verifier == null) {
            return ResponseEntity.status(503).build();
        }
        if (!verifier.verify(webhookId, timestamp, signature, body)) {
            log.warn("포트원 웹훅 서명 검증 실패: id={}", webhookId);
            return ResponseEntity.status(401).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("포트원 웹훅 본문을 읽지 못했다: id={}", webhookId);
            return ResponseEntity.badRequest().build();
        }
        String type = root.path("type").asText("");
        String paymentId = root.path("data").path("paymentId").asText("");

        switch (type) {
            case "Transaction.Paid" -> {
                OrderService.WebhookSettle result = orderService.settleByWebhook(paymentId);
                log.info("포트원 웹훅 결제완료: paymentId={} 결과={}", paymentId, result);
                if (result == OrderService.WebhookSettle.NOT_PAID) {
                    // 포트원은 결제됐다는데 조회 결과가 아직 아니다 — 재전송을 받아 다시 확인한다.
                    return ResponseEntity.status(500).build();
                }
            }
            case "Transaction.Cancelled", "Transaction.PartialCancelled" -> {
                if (orderService.isRecordedAsCancelled(paymentId)) {
                    log.info("포트원 웹훅 취소 확인(우리 쪽 처리와 일치): paymentId={} type={}", paymentId, type);
                } else {
                    // 관리자 화면이 아니라 포트원 콘솔 등에서 직접 취소한 경우. 주문 상태·재고는 자동으로 바꾸지 않는다
                    // (취소 기록은 order_claim 에 요청·처리 시각과 함께 남아야 한다 — 전자상거래법).
                    log.warn("포트원에서 취소됐지만 주문에 취소 기록이 없다 — 관리자 확인 필요: paymentId={} type={}",
                            paymentId, type);
                }
            }
            default -> log.info("포트원 웹훅 무시: type={} paymentId={}", type, paymentId);
        }
        return ResponseEntity.ok().build();
    }
}
