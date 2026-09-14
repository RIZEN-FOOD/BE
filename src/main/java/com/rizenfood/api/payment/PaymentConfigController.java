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
 * storeId·channelKey 는 원래 브라우저에 노출되는 공개값이다. API 시크릿은 절대 내보내지 않는다.
 * 설정만 바꾸면(PAYMENT_PROVIDER) 프론트 재배포 없이 결제 방식이 바뀐다.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentConfigController {

    private final String provider;
    private final String storeId;
    private final String channelKey;

    public PaymentConfigController(@Value("${app.payment.provider:mock}") String provider,
                                   @Value("${app.payment.portone.store-id:}") String storeId,
                                   @Value("${app.payment.portone.channel-key:}") String channelKey) {
        this.provider = provider;
        this.storeId = storeId;
        this.channelKey = channelKey;
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("provider", provider);
        if ("portone".equals(provider)) {
            body.put("storeId", storeId);
            body.put("channelKey", channelKey);
        }
        return body;
    }
}
