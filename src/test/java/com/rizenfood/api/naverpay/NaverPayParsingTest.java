package com.rizenfood.api.naverpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.rizenfood.api.shipping.ShippingPolicy;

/**
 * 네이버페이 요청·응답 해석과 배송비 정책 변환.
 */
class NaverPayParsingTest {

    // ── 조회 주소 파라미터 (표 3-7·3-13) ─────────────────────

    @Test
    @DisplayName("상품정보 요청: product[0][id]=…&product[1][id]=… 를 순번대로 읽는다")
    void productInfoIds() {
        assertThat(NaverPayQuery.productInfoIds("product[1][id]=34&product[0][id]=12&supplementSearch=false"))
                .containsExactly("12", "34");
    }

    @Test
    @DisplayName("대괄호가 %5B%5D 로 인코딩돼 와도 읽는다")
    void encodedBrackets() {
        assertThat(NaverPayQuery.productInfoIds("product%5B0%5D%5Bid%5D=5")).containsExactly("5");
    }

    @Test
    @DisplayName("id 가 없으면 ecmallproductId 를 쓴다")
    void ecMallProductIdFallback() {
        assertThat(NaverPayQuery.productInfoIds("product[0][ecmallproductId]=9")).containsExactly("9");
    }

    @Test
    @DisplayName("도서산간비 요청: productId[0]·product[0] 두 표기를 다 받고 우편번호를 읽는다")
    void additionalFeeParams() {
        String q = "productId[0]=1&product[1]=2&zipcode=63000&address1=6rK96riw64-EIOyEseuCqA";
        assertThat(NaverPayQuery.additionalFeeIds(q)).containsExactly("1", "2");
        assertThat(NaverPayQuery.param(q, "zipcode")).isEqualTo("63000");
    }

    @Test
    @DisplayName("한 번에 조회하는 상품 수는 50개로 자른다")
    void limitsIds() {
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            q.append("product[").append(i).append("][id]=").append(i + 1).append('&');
        }
        assertThat(NaverPayQuery.productInfoIds(q.toString())).hasSize(NaverPayQuery.MAX_IDS);
    }

    @Test
    @DisplayName("비었거나 이상한 쿼리는 빈 목록")
    void emptyOrGarbage() {
        assertThat(NaverPayQuery.productInfoIds(null)).isEmpty();
        assertThat(NaverPayQuery.productInfoIds("a=b&%ZZ=1")).isEmpty();
    }

    // ── 주문 등록 응답 (3.1.3) ───────────────────────────────

    @Test
    @DisplayName("SUCCESS:인증키:가맹점번호 를 나눠 읽는다")
    void parsesSuccess() {
        NaverPayRegisterResult r = NaverPayRegisterResult.parse("SUCCESS:ABC123xyz:500123\n");
        assertThat(r.key()).isEqualTo("ABC123xyz");
        assertThat(r.merchantNo()).isEqualTo("500123");
    }

    @Test
    @DisplayName("FAIL 은 네이버 사유를 담은 예외")
    void failThrows() {
        assertThatThrownBy(() -> NaverPayRegisterResult.parse("FAIL:[E0123]상품 정보 오류"))
                .isInstanceOf(NaverPayException.class)
                .hasMessageContaining("E0123");
    }

    @Test
    @DisplayName("인증키 형식이 어긋나거나 알 수 없는 응답이면 예외 — 이상한 값으로 주문서를 열지 않는다")
    void rejectsMalformed() {
        assertThatThrownBy(() -> NaverPayRegisterResult.parse("SUCCESS:<script>:1"))
                .isInstanceOf(NaverPayException.class);
        assertThatThrownBy(() -> NaverPayRegisterResult.parse("SUCCESS:ABCDEFGHIJ1234567890:1"))
                .as("인증키는 최대 19자리").isInstanceOf(NaverPayException.class);
        assertThatThrownBy(() -> NaverPayRegisterResult.parse("<html>502</html>"))
                .isInstanceOf(NaverPayException.class);
        assertThatThrownBy(() -> NaverPayRegisterResult.parse(null))
                .isInstanceOf(NaverPayException.class);
    }

    // ── 배송비 정책 변환 (표 3-4) ────────────────────────────

    private static ShippingPolicy policy(int base, Integer threshold, int island) {
        ShippingPolicy p = mock(ShippingPolicy.class);
        when(p.getId()).thenReturn(1L);
        when(p.getBaseFee()).thenReturn(base);
        when(p.getFreeThreshold()).thenReturn(threshold);
        when(p.getIslandExtraFee()).thenReturn(island);
        return p;
    }

    @Test
    @DisplayName("3,500원·5만원 이상 무료 → 조건부 무료, 선불")
    void conditionalFree() {
        NaverPayXml.Shipping s = NaverPayService.shippingOf(policy(3_500, 50_000, 3_000));
        assertThat(s.feeType()).isEqualTo("CONDITIONAL_FREE");
        assertThat(s.feePayType()).isEqualTo("PREPAYED");
        assertThat(s.feePrice()).isEqualTo(3_500);
        assertThat(s.conditionalFreeBasePrice()).isEqualTo(50_000);
        assertThat(s.surchargeApi()).isTrue();
        assertThat(s.groupId()).as("모든 상품을 한 묶음으로 — 배송비는 주문당 한 번").isEqualTo("1");
    }

    @Test
    @DisplayName("무료배송 기준이 없으면 유료, 배송비가 0이면 무료")
    void chargeAndFree() {
        assertThat(NaverPayService.shippingOf(policy(3_500, null, 0)).feeType()).isEqualTo("CHARGE");
        NaverPayXml.Shipping free = NaverPayService.shippingOf(policy(0, null, 0));
        assertThat(free.feeType()).isEqualTo("FREE");
        assertThat(free.feePayType()).isEqualTo("FREE");
        assertThat(free.feePrice()).isZero();
    }

    @Test
    @DisplayName("도서산간 추가 배송비가 0이면 도서산간 API 를 쓰지 않는다")
    void noSurchargeWhenZero() {
        assertThat(NaverPayService.shippingOf(policy(3_500, 50_000, 0)).surchargeApi()).isFalse();
    }

    // ── 설정 ────────────────────────────────────────────────

    @Test
    @DisplayName("키가 하나라도 비면 준비 안 됨, 모르는 과세 종류는 과세로")
    void propertiesReady() {
        assertThat(new NaverPayProperties(true, "test", "np_a", "c", "b", "", "TAX").ready()).isTrue();
        assertThat(new NaverPayProperties(true, "test", "np_a", "", "b", "", "TAX").ready()).isFalse();
        assertThat(new NaverPayProperties(false, "test", "np_a", "c", "b", "", "TAX").ready()).isFalse();
        assertThat(new NaverPayProperties(true, "weird", "np_a", "c", "b", "", "nope").taxType()).isEqualTo("TAX");
    }

    @Test
    @DisplayName("test 는 네이버 테스트 서버, production 은 운영 서버 주소")
    void urlsByMode() {
        NaverPayProperties test = new NaverPayProperties(true, "test", "np_a", "c", "b", "", "TAX");
        NaverPayProperties prod = new NaverPayProperties(true, "production", "np_a", "c", "b", "", "TAX");
        assertThat(test.registerUrl()).startsWith("https://test-api.pay.naver.com/");
        assertThat(prod.registerUrl()).startsWith("https://api.pay.naver.com/");
        assertThat(test.buttonScriptUrl()).startsWith("https://test-pay.naver.com/");
        assertThat(prod.buttonScriptUrl()).startsWith("https://npay-order.pstatic.net/");
    }
}
