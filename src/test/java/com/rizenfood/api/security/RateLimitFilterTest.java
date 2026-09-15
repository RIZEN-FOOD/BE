package com.rizenfood.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(new ClientIpResolver("CF-Connecting-IP"), true, now::get);
    }

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        var req = new MockHttpServletRequest(method, uri);
        req.addHeader("CF-Connecting-IP", ip);
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void 로그인은_분당_10회까지_허용하고_11번째는_429() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        var blocked = call("POST", "/api/auth/login", "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("TOO_MANY_REQUESTS").contains("잠시 후 다시 시도해 주세요");
    }

    @Test
    void IP가_다르면_따로_센다() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/auth/login", "1.1.1.1");
        }
        assertThat(call("POST", "/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void 위조한_XFF로는_우회할_수_없다() throws Exception {
        for (int i = 0; i < 10; i++) {
            call("POST", "/api/auth/login", "1.1.1.1");
        }
        var req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader("CF-Connecting-IP", "1.1.1.1");
        req.addHeader("X-Forwarded-For", "9.9.9.9");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void 시간이_지나면_다시_허용한다() throws Exception {
        for (int i = 0; i < 11; i++) {
            call("POST", "/api/auth/login", "1.1.1.1");
        }
        now.addAndGet(60_000);

        assertThat(call("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void 규칙이_없는_경로와_메서드는_제한하지_않는다() throws Exception {
        for (int i = 0; i < 200; i++) {
            assertThat(call("GET", "/api/products/cream-of-rice", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        // 같은 경로라도 GET 로그인은 규칙이 없다
        for (int i = 0; i < 20; i++) {
            assertThat(call("GET", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void 주문번호_경로_패턴이_맞게_걸린다() throws Exception {
        for (int i = 0; i < 60; i++) {
            call("GET", "/api/orders/ORD" + i, "3.3.3.3");
        }
        assertThat(call("GET", "/api/orders/ANY", "3.3.3.3").getStatus()).isEqualTo(429);
        // 결제 확정은 별도 카운터
        assertThat(call("POST", "/api/orders/ANY/pay", "3.3.3.3").getStatus()).isEqualTo(200);
    }

    @Test
    void 꺼두면_아무것도_막지_않는다() throws Exception {
        filter = new RateLimitFilter(new ClientIpResolver(""), false, now::get);
        for (int i = 0; i < 50; i++) {
            assertThat(call("POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void 끝난_창은_정리된다() throws Exception {
        call("POST", "/api/auth/login", "1.1.1.1");
        call("POST", "/api/inquiries", "1.1.1.1");
        assertThat(filter.trackedKeys()).isEqualTo(2);

        now.addAndGet(10 * 60_000);
        filter.evictExpired();

        assertThat(filter.trackedKeys()).isZero();
    }
}
