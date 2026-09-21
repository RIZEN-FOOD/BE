package com.rizenfood.api.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 나이스페이(신모듈) 결제 어댑터 — Server 승인 + Basic 인증.
 *
 * 흐름
 *   1) 브라우저가 나이스 결제창을 연다(AUTHNICE.requestPay). 주문번호 = orderId.
 *   2) 손님이 카드사 인증을 마치면 나이스가 우리 서버로 결과를 POST 한다
 *      ({@link NicePayReturnController}). 거기서 위변조 서명과 금액을 검사하고 tid 를 저장한다.
 *   3) 여기 approve() 가 그 tid 로 나이스 승인 API 를 호출한다. 이 호출을 해야 실제로 돈이 빠진다.
 *
 * ★ 인증(1·2)만으로는 결제가 되지 않는다. 승인 API(3)를 불러야 결제가 완료된다.
 * ★ 승인 응답의 금액도 서버 계산 금액과 다시 대조한다(대조는 OrderService 가 한다).
 * ★ 시크릿 키는 서버에만 둔다(.env 의 NICEPAY_SECRET_KEY).
 *
 * app.payment.provider=nicepay 일 때만 등록된다.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "nicepay")
public class NicePayGateway implements PaymentGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NicePayGateway.class);

    /** 나이스가 "성공"으로 주는 코드. 그 외는 전부 실패다. */
    static final String OK = "0000";

    private final PaymentRepository payments;
    private final String clientId;
    private final String secretKey;
    private final String cancelPassword;
    private final RestClient http;

    /**
     * ★ 키가 없어도 서버는 뜬다. 키를 넣기 전에도 사이트(상품·회원·관리자)는 열려 있어야 하기 때문이다.
     *   대신 결제·환불 요청은 전부 "결제 준비 중"으로 거절한다(fail closed).
     */
    public NicePayGateway(PaymentRepository payments,
                          @Value("${app.payment.nicepay.client-id:}") String clientId,
                          @Value("${app.payment.nicepay.secret-key:}") String secretKey,
                          @Value("${app.payment.nicepay.cancel-password:}") String cancelPassword,
                          @Value("${app.payment.nicepay.api-base:https://api.nicepay.co.kr}") String apiBase) {
        this.payments = payments;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.cancelPassword = cancelPassword == null ? "" : cancelPassword.trim();

        if (this.clientId.isEmpty() || this.secretKey.isEmpty()) {
            log.warn("NICEPAY_CLIENT_ID·NICEPAY_SECRET_KEY 가 비어 있다 — 결제를 받지 않는 상태로 시작한다.");
            this.http = null;
            return;
        }
        String basic = Base64.getEncoder().encodeToString(
                (this.clientId + ":" + this.secretKey).getBytes(StandardCharsets.UTF_8));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(20)); // 승인은 카드사를 거쳐 느릴 수 있다
        this.http = RestClient.builder()
                .baseUrl(apiBase)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    /** 결제를 받을 수 있는 상태인가 (키가 설정됨). 결제 화면이 이 값으로 결제수단을 띄울지 정한다. */
    public boolean configured() {
        return http != null;
    }

    /** 결제창에 넘길 공개값. 시크릿은 절대 내보내지 않는다. */
    public String clientId() {
        return clientId;
    }

    @Override
    public String provider() {
        return "NICEPAY";
    }

    /**
     * 승인. 인증 단계에서 저장해 둔 tid 로 나이스에 승인을 요청한다.
     *
     * @throws PaymentException 아직 인증 전이거나(=tid 없음) 나이스가 승인하지 않은 경우
     */
    @Override
    public Approval approve(String orderNo, int expectedAmount) {
        String tid = tidOf(orderNo);
        String ediDate = Instant.now().toString();

        Map<String, Object> body = new HashMap<>();
        body.put("amount", expectedAmount);
        body.put("ediDate", ediDate);
        body.put("signData", sha256Hex(tid + expectedAmount + ediDate + secretKey));

        JsonNode res;
        try {
            res = client().post()
                    .uri("/v1/payments/{tid}", tid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // ★ 읽기 시간 초과는 "승인됐는지 모르는" 상태다. 주문을 확정하지 않고,
            //   30분 정리 배치가 나중에 다시 확인해 확정하거나 재고를 되돌린다.
            log.error("나이스 승인 요청 실패: order={} tid={}", orderNo, tid, e);
            throw new PaymentException("결제 승인을 확인하지 못했습니다.");
        }
        if (res == null) {
            throw new PaymentException("결제 승인 응답이 비어 있습니다.");
        }

        String resultCode = res.path("resultCode").asText("");
        String status = res.path("status").asText("");
        if (!OK.equals(resultCode) || !"paid".equals(status)) {
            log.warn("나이스 승인 거절: order={} code={} status={} msg={}",
                    orderNo, resultCode, status, res.path("resultMsg").asText(""));
            throw new PaymentException("결제가 완료되지 않았습니다.");
        }

        int amount = res.path("amount").asInt(-1);
        // 응답 위변조 검증. 서명이 오지 않는 경우가 있어(가맹점 설정) 값이 있을 때만 본다.
        String signature = res.path("signature").asText("");
        String resEdiDate = res.path("ediDate").asText(ediDate);
        if (!signature.isBlank()) {
            String expected = sha256Hex(tid + amount + resEdiDate + secretKey);
            if (!expected.equalsIgnoreCase(signature)) {
                log.error("나이스 승인 응답 서명 불일치: order={} tid={}", orderNo, tid);
                throw new PaymentException("결제 응답을 신뢰할 수 없습니다.");
            }
        }

        return new Approval(
                res.path("tid").asText(tid),
                amount,
                res.path("payMethod").asText(""),
                res.path("receiptUrl").asText(null));
    }

    /**
     * 취소(환불). amount 가 null 이면 전액이다.
     *
     * 나이스는 부분취소 시 원거래 tid 와 다른 취소 tid 를 돌려준다. 우리는 원거래 tid 를 그대로 둔다
     * (주문 한 건의 결제를 가리키는 키라서). 취소 내역은 결제 상태로 남는다.
     */
    @Override
    public void cancel(String orderNo, Integer amount, String reason) {
        String tid = tidOf(orderNo);

        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason == null || reason.isBlank() ? "고객 요청" : reason);
        body.put("orderId", orderNo);
        if (amount != null) {
            body.put("cancelAmt", amount);
        }
        if (!cancelPassword.isEmpty()) {
            body.put("cancelPwd", cancelPassword);
        }

        JsonNode res;
        try {
            res = client().post()
                    .uri("/v1/payments/{tid}/cancel", tid)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("나이스 취소 요청 실패: order={} tid={}", orderNo, tid, e);
            throw new PaymentException("환불 요청에 실패했습니다.");
        }
        String resultCode = res == null ? "" : res.path("resultCode").asText("");
        if (!OK.equals(resultCode)) {
            String msg = res == null ? "" : res.path("resultMsg").asText("");
            log.warn("나이스 취소 거절: order={} code={} msg={}", orderNo, resultCode, msg);
            throw new PaymentException("환불이 거절되었습니다. " + msg);
        }
    }

    // ── 도우미 ────────────────────────────────────────────────

    /** 인증 단계에서 저장해 둔 거래키. 없으면 아직 결제창을 거치지 않은 것이다. */
    private String tidOf(String orderNo) {
        String tid = payments.findTidByOrderNo(orderNo);
        if (tid == null || tid.isBlank()) {
            throw new PaymentException("결제 진행 기록이 없습니다.");
        }
        return tid;
    }

    private RestClient client() {
        if (http == null) {
            throw new PaymentUnavailableException("결제 준비 중입니다. 잠시 후 다시 이용해 주세요.");
        }
        return http;
    }

    /**
     * 나이스 위변조 서명. 인증 응답 검증과 승인 요청에 같은 방식을 쓴다.
     * (인증 응답: authToken + clientId + amount + secretKey / 승인: tid + amount + ediDate + secretKey)
     */
    static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    /** 인증 응답(returnUrl) 서명 검증. 컨트롤러가 쓴다. */
    boolean verifyAuthSignature(String authToken, String amount, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return sha256Hex(authToken + clientId + amount + secretKey).equalsIgnoreCase(signature);
    }
}
