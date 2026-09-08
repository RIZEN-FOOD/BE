package com.rizenfood.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문번호 생성 규칙 (CLAUDE.md 규칙 5).
 *
 * 순번이면 남의 주문번호를 추측해 훑을 수 있다. 그래서:
 *  - 순번이 아니라 충분한 엔트로피의 난수를 쓴다.
 *  - 혼동하기 쉬운 글자(0/O, 1/I)를 뺀다.
 *  - 대량 생성해도 충돌하지 않는다.
 */
class OrderNoGeneratorTest {

    private final OrderNoGenerator generator = new OrderNoGenerator();

    // R{YYYYMMDD}-{10자리}, 난수부에는 0,1,I,O 가 없다.
    private static final Pattern FORMAT = Pattern.compile("^R\\d{8}-[2-9A-HJ-NP-Z]{10}$");

    @Test
    @DisplayName("형식: R + 날짜8자리 + '-' + 혼동없는 10자리 난수")
    void format() {
        for (int i = 0; i < 100; i++) {
            assertThat(generator.generate()).matches(FORMAT);
        }
    }

    @Test
    @DisplayName("혼동 글자(0,1,I,O)를 쓰지 않는다")
    void noConfusableChars() {
        for (int i = 0; i < 200; i++) {
            String random = generator.generate().substring(10); // 'R'+8+'-' 이후
            assertThat(random).doesNotContainAnyWhitespaces();
            assertThat(random).matches("[2-9A-HJ-NP-Z]{10}");
        }
    }

    @Test
    @DisplayName("순번이 아니다 — 대량 생성해도 모두 다르다")
    void notSequentialAndUnique() {
        int n = 10_000;
        Set<String> seen = new HashSet<>();
        String prev = null;
        for (int i = 0; i < n; i++) {
            String no = generator.generate();
            seen.add(no);
            if (prev != null) {
                // 바로 이전 번호에서 예측 가능한 증가가 아니어야 한다.
                assertThat(no).isNotEqualTo(next(prev));
            }
            prev = no;
        }
        // 충돌이 없어야 한다(사실상 유일).
        assertThat(seen).hasSize(n);
    }

    /** "순번이라면 이럴 것"이라는 반례 — 마지막 글자를 알파벳순으로 +1 한 값. */
    private static String next(String orderNo) {
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        char last = orderNo.charAt(orderNo.length() - 1);
        int idx = alphabet.indexOf(last);
        char bumped = alphabet.charAt((idx + 1) % alphabet.length());
        return orderNo.substring(0, orderNo.length() - 1) + bumped;
    }
}
