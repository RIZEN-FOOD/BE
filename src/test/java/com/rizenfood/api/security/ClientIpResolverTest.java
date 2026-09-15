package com.rizenfood.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void 헤더를_지정하지_않으면_연결주소를_쓰고_XFF는_무시한다() {
        var resolver = new ClientIpResolver("");
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Forwarded-For", "1.2.3.4");

        assertThat(resolver.resolve(req)).isEqualTo("10.0.0.5");
    }

    @Test
    void 운영에서는_CF_Connecting_IP를_쓰고_위조한_XFF는_무시한다() {
        var resolver = new ClientIpResolver("CF-Connecting-IP");
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.3"); // cloudflared 컨테이너
        req.addHeader("X-Forwarded-For", "6.6.6.6");
        req.addHeader("CF-Connecting-IP", "203.0.113.7");

        assertThat(resolver.resolve(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void 지정한_헤더가_없으면_연결주소로_돌아간다() {
        var resolver = new ClientIpResolver("CF-Connecting-IP");
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.3");

        assertThat(resolver.resolve(req)).isEqualTo("172.18.0.3");
    }

    @Test
    void 너무_긴_값은_잘라낸다() {
        var resolver = new ClientIpResolver("CF-Connecting-IP");
        var req = new MockHttpServletRequest();
        req.addHeader("CF-Connecting-IP", "x".repeat(200));

        assertThat(resolver.resolve(req)).hasSize(64);
    }

    @Test
    void 요청이_없으면_null() {
        assertThat(new ClientIpResolver("").resolve(null)).isNull();
    }
}
