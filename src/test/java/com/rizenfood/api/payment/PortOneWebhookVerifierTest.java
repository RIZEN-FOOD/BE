package com.rizenfood.api.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class PortOneWebhookVerifierTest {

    private static final byte[] KEY = "test-webhook-secret-32-bytes-ok!".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(KEY);
    private static final Instant NOW = Instant.parse("2026-09-14T09:00:00Z");
    private static final String ID = "msg_2KWPBgLlAfxdpx2AI54pPJ85f4W";
    private static final String TS = String.valueOf(NOW.getEpochSecond());
    private static final byte[] BODY =
            "{\"type\":\"Transaction.Paid\",\"data\":{\"paymentId\":\"R20260914ABCD\"}}".getBytes(StandardCharsets.UTF_8);

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    /** 검증기와 별개로 규격대로 서명을 만든다. */
    private static String sign(byte[] key, String id, String ts, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update((id + "." + ts + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(body);
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal());
    }

    @Test
    void 올바른_서명은_통과() throws Exception {
        assertThat(verifier.verify(ID, TS, sign(KEY, ID, TS, BODY), BODY)).isTrue();
    }

    @Test
    void 본문을_한_글자라도_바꾸면_실패() throws Exception {
        String sig = sign(KEY, ID, TS, BODY);
        byte[] tampered = new String(BODY, StandardCharsets.UTF_8).replace("ABCD", "ABCE").getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(ID, TS, sig, tampered)).isFalse();
    }

    @Test
    void 다른_시크릿으로_만든_서명은_실패() throws Exception {
        byte[] otherKey = "another-secret-another-secret-!!".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(ID, TS, sign(otherKey, ID, TS, BODY), BODY)).isFalse();
    }

    @Test
    void 아이디나_시각을_바꿔치기하면_실패() throws Exception {
        String sig = sign(KEY, ID, TS, BODY);
        assertThat(verifier.verify("msg_other", TS, sig, BODY)).isFalse();
        String otherTs = String.valueOf(NOW.getEpochSecond() - 1);
        assertThat(verifier.verify(ID, otherTs, sig, BODY)).isFalse();
    }

    @Test
    void 오분이_넘은_알림은_서명이_맞아도_거절() throws Exception {
        String oldTs = String.valueOf(NOW.minusSeconds(6 * 60).getEpochSecond());
        assertThat(verifier.verify(ID, oldTs, sign(KEY, ID, oldTs, BODY), BODY)).isFalse();

        String recentTs = String.valueOf(NOW.minusSeconds(4 * 60).getEpochSecond());
        assertThat(verifier.verify(ID, recentTs, sign(KEY, ID, recentTs, BODY), BODY)).isTrue();
    }

    @Test
    void RFC3339_형식의_시각도_받는다() throws Exception {
        String isoTs = "2026-09-14T09:00:30Z";
        assertThat(verifier.verify(ID, isoTs, sign(KEY, ID, isoTs, BODY), BODY)).isTrue();
    }

    @Test
    void 서명이_여러_개면_하나만_맞아도_통과하고_모르는_버전은_무시() throws Exception {
        String header = "v1a,xxxx v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= " + sign(KEY, ID, TS, BODY);
        assertThat(verifier.verify(ID, TS, header, BODY)).isTrue();
    }

    @Test
    void 헤더가_빠지면_실패() throws Exception {
        String sig = sign(KEY, ID, TS, BODY);
        assertThat(verifier.verify(null, TS, sig, BODY)).isFalse();
        assertThat(verifier.verify(ID, null, sig, BODY)).isFalse();
        assertThat(verifier.verify(ID, TS, null, BODY)).isFalse();
        assertThat(verifier.verify(ID, "not-a-time", sig, BODY)).isFalse();
    }

    @Test
    void 접두사_없는_시크릿은_문자열_그대로_키로_쓴다() throws Exception {
        String raw = "plain-secret-value";
        var plain = new PortOneWebhookVerifier(raw, Clock.fixed(NOW, ZoneOffset.UTC));
        String sig = sign(raw.getBytes(StandardCharsets.UTF_8), ID, TS, BODY);
        assertThat(plain.verify(ID, TS, sig, BODY)).isTrue();
    }
}
