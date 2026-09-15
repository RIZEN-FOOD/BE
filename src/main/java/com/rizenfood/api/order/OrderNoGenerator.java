package com.rizenfood.api.order;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * 주문번호 생성기.
 *
 * ★ 순번을 쓰지 않는다 (CLAUDE.md 규칙 5). 순번이면 남의 주문번호를 추측해
 *   훑을 수 있다. 날짜 프리픽스 + 충분한 엔트로피의 난수를 붙인다.
 *
 * 형식:  R{YYYYMMDD}-{10자리 난수}
 *   예)  R20260902-7QK3M9XZ2A
 *
 * 혼동하기 쉬운 글자(0/O, 1/I)를 뺀 32자 집합을 쓴다. 10자리면 32^10 ≈ 1.1e15
 * 가지라 하루치 주문 안에서 충돌은 사실상 없다. 그래도 유니크 제약 + 재시도로 막는다.
 */
@Component
public class OrderNoGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int RANDOM_LEN = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder("R");
        sb.append(LocalDate.now(KST).format(DATE)).append('-');
        for (int i = 0; i < RANDOM_LEN; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
