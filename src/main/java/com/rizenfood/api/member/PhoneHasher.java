package com.rizenfood.api.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 휴대폰 번호로 "같은 사람인지"만 맞춰보기 위한 조회용 해시.
 *
 * PhoneCipher 는 AES-GCM 을 쓰고 매번 새 IV 를 뽑는다. 그래서 같은 번호라도
 * 저장된 값이 매번 달라지고, <b>암호문으로는 검색할 수 없다</b>. 할인코드의
 * "1인 1회"를 비회원까지 세려면 번호로 찾을 수단이 필요해서 이 값을 따로 둔다.
 *
 * ★ 원문을 되돌릴 수 없어야 한다. 그래서 단순 SHA-256 이 아니라 키가 들어간
 *   HMAC 을 쓴다. 번호 공간이 1억 개뿐이라 키 없는 해시는 전부 대입해 뚫린다.
 * ★ 키는 암호화 키를 그대로 쓰지 않고, 용도 문자열을 섞어 한 번 갈라 쓴다.
 *   해시가 새어도 암호화 키를 되찾을 수 없다.
 * ★ 이 값으로 주문을 찾을 수 있으므로 화면에 내보내지 않는다. 서버 안에서만 쓴다.
 */
@Component
public class PhoneHasher {

    private static final String ALGORITHM = "HmacSHA256";
    /** 키를 용도별로 가르는 라벨. 바꾸면 기존 해시와 안 맞으니 건드리지 마라. */
    private static final String PURPOSE = "rizen|phone-lookup|v1";

    private final SecretKeySpec key;

    public PhoneHasher(@Value("${app.crypto.phone-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.phone-key 가 없다. .env 의 PHONE_ENC_KEY 를 설정하라 (base64 32바이트).");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("휴대폰 암호화 키는 32바이트(AES-256)여야 한다.");
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(keyBytes);
            sha.update(PURPOSE.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(sha.digest(), ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("조회용 해시 키를 만들지 못했다.", e);
        }
    }

    /**
     * 숫자만 남긴 번호의 해시(64자 hex). 번호가 비어 있으면 null.
     * 하이픈이 있든 없든 같은 값이 나온다.
     */
    public String hash(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(digits.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("번호 해시에 실패했다.", e);
        }
    }
}
