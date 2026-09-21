package com.rizenfood.api.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 설정(공개값) 조회.
 *
 * 브라우저가 어떤 결제 방식으로 결제창을 열지 판단하는 데 쓴다.
 * storeId·channelKey·clientId 는 원래 브라우저에 노출되는 공개값이다.
 * API 시크릿·웹훅 시크릿은 절대 내보내지 않는다.
 *
 * 설정만 바꾸면(PAYMENT_PROVIDER, 키) 프론트 재배포 없이 결제 방식·결제수단이 바뀐다.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentConfigController {

    /**
     * 나이스페이 결제창에서 쓸 수 있는 결제수단.
     * key 는 나이스 method 값 그대로다 — 브라우저가 이 값을 결제창에 넘긴다.
     *
     * ★ 계약에 없는 수단을 띄우면 결제창에서 거절된다. 실제로 계약된 것만 남긴다.
     *   (토스페이는 나이스 신모듈이 지원하지 않아 목록에 없다)
     */
    private static final List<Map<String, String>> NICEPAY_METHODS = List.of(
            Map.of("key", "card", "label", "신용·체크카드"),
            Map.of("key", "bank", "label", "계좌이체"),
            Map.of("key", "kakaopay", "label", "카카오페이"),
            Map.of("key", "naverpayCard", "label", "네이버페이"));

    private final String provider;
    private final String storeId;
    private final Map<String, String> channels = new LinkedHashMap<>();
    private final ObjectProvider<NicePayGateway> nicePay;

    public PaymentConfigController(@Value("${app.payment.provider:mock}") String provider,
                                   @Value("${app.payment.portone.store-id:}") String storeId,
                                   @Value("${app.payment.portone.channel-key:}") String cardChannel,
                                   @Value("${app.payment.portone.channel-key-kakaopay:}") String kakaoChannel,
                                   @Value("${app.payment.portone.channel-key-naverpay:}") String naverChannel,
                                   @Value("${app.payment.portone.channel-key-tosspay:}") String tossChannel,
                                   @Value("${app.payment.portone.channel-key-transfer:}") String transferChannel,
                                   ObjectProvider<NicePayGateway> nicePay) {
        this.provider = provider;
        this.storeId = storeId;
        this.nicePay = nicePay;
        // 화면에 보이는 순서대로 넣는다.
        putIfPresent("CARD", cardChannel);
        putIfPresent("KAKAOPAY", kakaoChannel);
        putIfPresent("NAVERPAY", naverChannel);
        putIfPresent("TOSSPAY", tossChannel);
        putIfPresent("TRANSFER", transferChannel);
    }

    private void putIfPresent(String method, String key) {
        if (key != null && !key.isBlank()) {
            channels.put(method, key.trim());
        }
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", provider);
        if ("portone".equals(provider)) {
            body.put("storeId", storeId);
            body.put("channels", channels);
        } else if ("nicepay".equals(provider)) {
            NicePayGateway gateway = nicePay.getIfAvailable();
            // 키가 없으면 결제수단을 내보내지 않는다 — 화면이 결제 버튼을 막는다.
            boolean ready = gateway != null && gateway.configured();
            body.put("clientId", ready ? gateway.clientId() : "");
            body.put("methods", ready ? NICEPAY_METHODS : List.of());
        }
        return body;
    }
}
