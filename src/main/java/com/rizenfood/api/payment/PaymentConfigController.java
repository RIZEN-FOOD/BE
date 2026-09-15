package com.rizenfood.api.payment;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 설정(공개값) 조회.
 *
 * 브라우저가 어떤 결제 방식으로 결제창을 열지 판단하는 데 쓴다.
 * storeId·channelKey 는 원래 브라우저에 노출되는 공개값이다. API 시크릿·웹훅 시크릿은 절대 내보내지 않는다.
 * 설정만 바꾸면(PAYMENT_PROVIDER, 채널 키) 프론트 재배포 없이 결제 방식·결제수단이 바뀐다.
 *
 * 포트원은 결제사(PG·간편결제)마다 채널이 따로다. 그래서 결제수단별 채널 키를 내려주고,
 * 키가 비어 있는 결제수단은 화면에 띄우지 않는다(계약 전 결제수단이 노출되지 않게).
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentConfigController {

    private final String provider;
    private final String storeId;
    private final Map<String, String> channels = new LinkedHashMap<>();

    public PaymentConfigController(@Value("${app.payment.provider:mock}") String provider,
                                   @Value("${app.payment.portone.store-id:}") String storeId,
                                   @Value("${app.payment.portone.channel-key:}") String cardChannel,
                                   @Value("${app.payment.portone.channel-key-kakaopay:}") String kakaoChannel,
                                   @Value("${app.payment.portone.channel-key-naverpay:}") String naverChannel,
                                   @Value("${app.payment.portone.channel-key-tosspay:}") String tossChannel,
                                   @Value("${app.payment.portone.channel-key-transfer:}") String transferChannel) {
        this.provider = provider;
        this.storeId = storeId;
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
        }
        return body;
    }
}
