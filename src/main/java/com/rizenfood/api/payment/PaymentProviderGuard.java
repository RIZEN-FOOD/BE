package com.rizenfood.api.payment;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영에서 모의 결제로 떨어지는 것을 막는다.
 *
 * 모의 결제는 돈을 받지 않고 "결제 완료"를 만든다. 운영에서 그렇게 되면
 * <b>돈은 안 들어왔는데 주문은 확정되고 재고가 나간다.</b>
 *
 * 예전에는 운영 설정에 PG 이름을 못 박아 막았다. 그러면 PG 를 바꿀 때마다 코드를 고쳐야 해서,
 * 대신 <b>허용 목록을 벗어나면 서버가 아예 뜨지 않게</b> 한다. 오타·빈 값도 같이 걸린다.
 */
@Component
@Profile("prod")
class PaymentProviderGuard {

    /** 실제로 돈이 오가는 구현체만 허용한다. */
    private static final Set<String> ALLOWED = Set.of("nicepay", "portone");

    PaymentProviderGuard(@Value("${app.payment.provider:}") String provider) {
        String value = provider == null ? "" : provider.trim();
        if (!ALLOWED.contains(value)) {
            throw new IllegalStateException(
                    "운영에서는 PAYMENT_PROVIDER 가 " + ALLOWED + " 중 하나여야 합니다. 지금 값: '"
                            + value + "' — 모의 결제로 떨어지면 돈을 받지 않고 주문이 확정됩니다.");
        }
    }
}
