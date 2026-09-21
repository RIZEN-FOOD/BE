package com.rizenfood.api.payment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.order.OrderService;

/**
 * 나이스페이 결제창 인증 결과를 받는 곳 (Server 승인 모델의 returnUrl).
 *
 * 손님이 카드사 인증을 마치면 나이스가 이 주소로 <b>브라우저를 통해 POST</b> 한다.
 * 다른 사이트(pay.nicepay.co.kr)에서 오는 이동이라 <b>우리 쿠키가 실리지 않는다</b> —
 * 로그인 정보로 주인을 확인할 수 없다. 그래서 신뢰의 근거는 두 가지뿐이다.
 *
 *   1) 나이스가 준 위변조 서명이 우리 시크릿 키로 다시 계산한 값과 같은가
 *   2) 그 금액이 우리가 저장한 주문 금액과 같은가
 *
 * 둘을 통과해야 승인 API 를 부른다. 승인 뒤에도 OrderService 가 금액을 한 번 더 대조하고,
 * 어긋나면 확정하지 않고 환불한다.
 *
 * ★ 인증만으로는 돈이 빠지지 않는다. 승인 API 를 불러야 결제가 끝난다.
 * ★ 실패했을 때도 손님에게는 결과 화면을 보여줘야 하므로 항상 브라우저를 되돌려 보낸다.
 */
@RestController
@RequestMapping("/api/payment/nicepay")
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "nicepay")
public class NicePayReturnController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(NicePayReturnController.class);

    private final NicePayGateway gateway;
    private final OrderService orderService;
    private final String siteUrl;

    public NicePayReturnController(NicePayGateway gateway, OrderService orderService,
                                   @Value("${app.site-url:}") String siteUrl) {
        this.gateway = gateway;
        this.orderService = orderService;
        this.siteUrl = siteUrl == null ? "" : siteUrl.replaceAll("/+$", "");
    }

    @PostMapping(value = "/return", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> onAuthResult(
            @RequestParam(name = "authResultCode", required = false) String authResultCode,
            @RequestParam(name = "authResultMsg", required = false) String authResultMsg,
            @RequestParam(name = "tid", required = false) String tid,
            @RequestParam(name = "orderId", required = false) String orderId,
            @RequestParam(name = "amount", required = false) String amount,
            @RequestParam(name = "authToken", required = false) String authToken,
            @RequestParam(name = "signature", required = false) String signature) {

        if (orderId == null || orderId.isBlank()) {
            log.warn("나이스 인증 결과에 주문번호가 없다");
            return redirect("/", "결제 정보를 확인하지 못했습니다.");
        }

        // 1) 인증 실패 — 손님이 창을 닫았거나 카드사가 거절했다. 승인하지 않고 되돌려 보낸다.
        if (!NicePayGateway.OK.equals(authResultCode)) {
            log.info("나이스 인증 실패: order={} code={} msg={}", orderId, authResultCode, authResultMsg);
            return redirectToOrder(orderId, authResultMsg == null || authResultMsg.isBlank()
                    ? "결제가 취소되었습니다." : authResultMsg);
        }

        // 2) 위변조 검증 — 우리 시크릿 키로 다시 계산해 같아야 한다.
        if (!gateway.verifyAuthSignature(authToken, amount, signature)) {
            log.error("나이스 인증 서명 불일치 — 승인하지 않는다: order={} tid={}", orderId, tid);
            return redirectToOrder(orderId, "결제 정보를 신뢰할 수 없어 중단했습니다.");
        }

        // 3) 거래키를 주문에 적어둔다. 이 값으로 승인 API 를 부른다.
        if (!orderService.rememberPgTid(orderId, tid)) {
            log.warn("나이스 인증 결과를 붙일 주문이 없다: order={}", orderId);
            return redirect("/", "주문을 찾을 수 없습니다.");
        }

        // 4) 승인 — 금액 대조와 확정은 OrderService 가 한다(웹훅과 같은 경로).
        OrderService.WebhookSettle settled = orderService.settleByWebhook(orderId);
        if (settled == OrderService.WebhookSettle.SETTLED
                || settled == OrderService.WebhookSettle.ALREADY_DONE) {
            return redirectToOrder(orderId, null);
        }
        log.warn("나이스 승인 후 주문 확정 실패: order={} 결과={}", orderId, settled);
        return redirectToOrder(orderId, "결제를 확정하지 못했습니다. 고객센터로 문의해 주세요.");
    }

    /**
     * 주문 결과 화면으로 되돌려 보낸다.
     *
     * 성공이면 paid=1 을 붙인다 — 서버가 이미 승인·확정을 마쳤으므로 화면이 확정 요청을
     * 다시 보내지 않게 하려는 표시다. 실패 사유가 있으면 화면이 그대로 보여준다.
     */
    private ResponseEntity<Void> redirectToOrder(String orderNo, String failMessage) {
        String path = "/checkout/complete?orderNo=" + encode(orderNo);
        if (failMessage == null || failMessage.isBlank()) {
            path += "&paid=1";
        }
        return redirect(path, failMessage);
    }

    private ResponseEntity<Void> redirect(String path, String failMessage) {
        String url = siteUrl + path;
        if (failMessage != null && !failMessage.isBlank()) {
            url += (url.contains("?") ? "&" : "?") + "fail=" + encode(failMessage);
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(url).toString())
                .build();
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
