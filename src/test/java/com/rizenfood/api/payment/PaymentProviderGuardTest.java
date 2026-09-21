package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운영에서 모의 결제로 떨어지지 않게 하는 마지막 방어선.
 *
 * 모의 결제는 돈을 받지 않고 "결제 완료"를 만든다. 운영에서 그렇게 되면
 * 돈은 안 들어왔는데 주문이 확정되고 재고가 나간다. 조용히 일어나서 한참 뒤에야 안다.
 *
 * 예전에는 운영 설정에 PG 이름을 못 박아 막았는데, PG 를 바꿀 때마다 코드를 고쳐야 했다.
 * 지금은 허용 목록을 벗어나면 서버가 아예 뜨지 않는다 — 조용히 잘못되는 것보다 낫다.
 */
class PaymentProviderGuardTest {

    @Test
    @DisplayName("실제로 돈이 오가는 PG 는 통과한다")
    void allowsRealGateways() {
        assertThatCode(() -> new PaymentProviderGuard("nicepay")).doesNotThrowAnyException();
        assertThatCode(() -> new PaymentProviderGuard("portone")).doesNotThrowAnyException();
        // 앞뒤 공백이 딸려 와도 통과한다 (.env 에서 흔하다)
        assertThatCode(() -> new PaymentProviderGuard("  nicepay  ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모의 결제면 서버를 띄우지 않는다")
    void refusesMock() {
        assertThatThrownBy(() -> new PaymentProviderGuard("mock"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_PROVIDER");
    }

    @Test
    @DisplayName("비었거나 오타여도 서버를 띄우지 않는다 — 조용히 모의 결제로 떨어지는 게 가장 위험하다")
    void refusesBlankOrTypo() {
        assertThatThrownBy(() -> new PaymentProviderGuard("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentProviderGuard("   ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentProviderGuard(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentProviderGuard("nicpay")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentProviderGuard("NICEPAY")).isInstanceOf(IllegalStateException.class);
    }
}
