package com.rizenfood.api.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 민감한 요청의 IP 당 횟수 제한.
 *
 * 막으려는 것
 *   - 유출된 비밀번호 목록으로 여러 계정을 한 번씩 찔러보기 (계정 잠금은 계정 단위라 못 막는다)
 *   - 남의 계정을 일부러 5회 틀려 잠가버리기
 *   - 이메일 중복확인으로 가입자 이메일 알아내기
 *   - 가짜 주문으로 재고를 30분씩 묶기, 주문번호 대입
 *   - 문의·후기 도배
 *
 * 서버가 한 대라 메모리 카운터로 충분하다. 서버를 여러 대로 늘리면 Redis 등 공유 저장소로 옮긴다.
 * 공개 조회(상품·공지 등)는 제한하지 않는다 — 대량 트래픽은 Cloudflare 가 앞에서 받는다.
 *
 * 인증보다 먼저 돈다(비싼 BCrypt 비교 전에 끊는다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** name 은 카운터 키의 일부다. 같은 name 을 두 규칙에 쓰지 않는다. */
    record Rule(String name, String method, String pattern, int limit, Duration window) {
    }

    static final List<Rule> RULES = List.of(
            new Rule("admin-login", "POST", "/api/admin/auth/login", 10, Duration.ofMinutes(1)),
            // 비밀번호 변경은 현재 비밀번호를 확인하므로 추측 공격 통로가 될 수 있다.
            new Rule("admin-password", "PATCH", "/api/admin/auth/password", 10, Duration.ofMinutes(10)),
            new Rule("admin-password-reset", "PUT", "/api/admin/accounts/*/password", 20, Duration.ofMinutes(10)),
            new Rule("member-login", "POST", "/api/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("signup", "POST", "/api/auth/signup", 5, Duration.ofMinutes(10)),
            new Rule("check-email", "POST", "/api/auth/check-email", 5, Duration.ofMinutes(1)),
            new Rule("refresh", "POST", "/api/auth/refresh", 30, Duration.ofMinutes(1)),
            new Rule("oauth-start", "GET", "/api/auth/oauth/*/start", 20, Duration.ofMinutes(1)),
            new Rule("oauth-callback", "GET", "/api/auth/oauth/*/callback", 20, Duration.ofMinutes(1)),
            new Rule("oauth-complete", "POST", "/api/auth/oauth/complete", 10, Duration.ofMinutes(1)),
            new Rule("order-create", "POST", "/api/orders", 10, Duration.ofMinutes(1)),
            new Rule("order-lookup", "GET", "/api/orders/*", 60, Duration.ofMinutes(1)),
            new Rule("order-pay", "POST", "/api/orders/*/pay", 20, Duration.ofMinutes(1)),
            new Rule("order-cancel-pending", "POST", "/api/orders/*/cancel-pending", 20, Duration.ofMinutes(1)),
            new Rule("order-claim", "POST", "/api/orders/*/claims", 10, Duration.ofMinutes(10)),
            new Rule("inquiry", "POST", "/api/inquiries", 5, Duration.ofMinutes(10)),
            new Rule("review", "POST", "/api/member/reviews", 10, Duration.ofMinutes(10)),
            new Rule("review-image", "POST", "/api/member/reviews/images", 20, Duration.ofMinutes(10)),
            new Rule("cart-add", "POST", "/api/cart/items", 60, Duration.ofMinutes(1)),
            // 쿠키 없이 GET 하면 게스트 장바구니가 한 줄씩 생긴다. 반복 호출로 DB 를 불리지 못하게 막는다.
            new Rule("cart-view", "GET", "/api/cart", 60, Duration.ofMinutes(1)));

    private static final long LONGEST_WINDOW_MS = RULES.stream()
            .mapToLong(r -> r.window().toMillis()).max().orElse(0);

    private static final class Window {
        final long startedAt;
        final AtomicInteger count = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ClientIpResolver ipResolver;
    private final boolean enabled;
    private final LongSupplier clock;

    @Autowired
    public RateLimitFilter(ClientIpResolver ipResolver,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this(ipResolver, enabled, System::currentTimeMillis);
    }

    /** 테스트에서 시간을 움직이기 위한 생성자 */
    RateLimitFilter(ClientIpResolver ipResolver, boolean enabled, LongSupplier clock) {
        this.ipResolver = ipResolver;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = enabled ? match(request) : null;
        if (rule != null) {
            String ip = ipResolver.resolve(request);
            long now = clock.getAsLong();
            long windowMs = rule.window().toMillis();

            Window window = windows.compute(rule.name() + "|" + ip, (key, old) ->
                    old == null || now - old.startedAt >= windowMs ? new Window(now) : old);

            if (window.count.incrementAndGet() > rule.limit()) {
                long retryAfter = Math.max(1, (window.startedAt + windowMs - now + 999) / 1000);
                log.warn("요청 횟수 제한: rule={} ip={} uri={}", rule.name(), ip, request.getRequestURI());
                reject(response, retryAfter);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Rule match(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if (rule.method().equals(method) && matcher.match(rule.pattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private static void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\"}");
    }

    /** 끝난 창을 비워 메모리가 계속 늘지 않게 한다. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    void evictExpired() {
        long now = clock.getAsLong();
        windows.values().removeIf(w -> now - w.startedAt >= LONGEST_WINDOW_MS);
    }

    /** 테스트용 */
    int trackedKeys() {
        return windows.size();
    }
}
