package com.rizenfood.api.order;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.rizenfood.api.cart.Cart;
import com.rizenfood.api.cart.CartService;
import com.rizenfood.api.order.dto.ClaimDtos;
import com.rizenfood.api.order.dto.OrderDtos;
import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 주문 API.
 *
 * 장바구니와 마찬가지로 회원·비회원 모두 쓴다. 경로는 permitAll 이고
 * 컨트롤러가 요청마다 주체를 가려낸다.
 *   - 생성: 요청자의 장바구니(회원/게스트)를 서버가 읽어 주문을 만든다.
 *   - 조회/결제: 회원 주문은 소유자만, 비회원 주문은 추측 불가능한 주문번호로 접근한다.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final ClaimService claimService;
    private final CartService cartService;
    private final AuthCookies cookies;

    public OrderController(OrderService orderService, ClaimService claimService,
                          CartService cartService, AuthCookies cookies) {
        this.orderService = orderService;
        this.claimService = claimService;
        this.cartService = cartService;
        this.cookies = cookies;
    }

    @PostMapping
    public ResponseEntity<OrderDtos.OrderView> create(
            @Valid @RequestBody OrderDtos.CreateRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request) {

        Long memberId = me != null ? me.id() : null;
        Long cartId = resolveCartId(me, request);
        OrderDtos.OrderView view = orderService.createFromCart(cartId, memberId, req);
        return ResponseEntity.status(201).body(view);
    }

    @GetMapping("/{orderNo}")
    public OrderDtos.OrderView get(
            @PathVariable String orderNo,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        return orderService.get(orderNo, me != null ? me.id() : null);
    }

    /**
     * 결제 확정. 브라우저가 PG 결제창을 마친 뒤 호출한다.
     * 서버가 PG 에 직접 조회해 상태·금액을 검증한 뒤에만 확정하고, 그때 장바구니를 비운다.
     */
    @PostMapping("/{orderNo}/pay")
    public OrderDtos.OrderView pay(
            @PathVariable String orderNo,
            @RequestBody(required = false) OrderDtos.PayRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request) {
        return orderService.pay(orderNo, me != null ? me.id() : null,
                req != null ? req : new OrderDtos.PayRequest(null),
                currentCartId(me, request));
    }

    /**
     * 결제창을 닫았거나 결제가 실패했을 때 호출한다.
     * 미결제 주문을 정리하고 잡아둔 재고를 되돌린다. 실제로는 결제가 끝난 상태라면
     * 서버가 PG 에서 확인해 확정한다(확정 요청 누락 대비).
     */
    @PostMapping("/{orderNo}/cancel-pending")
    public OrderDtos.OrderView cancelPending(
            @PathVariable String orderNo,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request) {
        return orderService.cancelPending(orderNo, me != null ? me.id() : null,
                currentCartId(me, request));
    }

    /** 결제 확정 뒤 비울 장바구니. 게스트 장바구니가 없으면 null(비울 것 없음). */
    private Long currentCartId(JwtTokenProvider.AuthenticatedMember me, HttpServletRequest request) {
        if (me != null) {
            return cartService.resolveMemberCart(me.id()).getId();
        }
        String token = cookies.readCartGuest(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        return cartService.findGuestCart(token).map(Cart::getId).orElse(null);
    }

    // ── 취소·반품·교환 ────────────────────────────────────────

    @PostMapping("/{orderNo}/claims")
    public ResponseEntity<ClaimDtos.View> createClaim(
            @PathVariable String orderNo,
            @Valid @RequestBody ClaimDtos.CreateRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        ClaimDtos.View view = claimService.create(orderNo, me != null ? me.id() : null, req);
        return ResponseEntity.status(201).body(view);
    }

    @GetMapping("/{orderNo}/claims")
    public List<ClaimDtos.View> listClaims(
            @PathVariable String orderNo,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        return claimService.listForOrder(orderNo, me != null ? me.id() : null);
    }

    /** 주문에 쓸 장바구니 id. 회원은 자기 장바구니, 게스트는 쿠키의 장바구니. */
    private Long resolveCartId(JwtTokenProvider.AuthenticatedMember me, HttpServletRequest request) {
        if (me != null) {
            return cartService.resolveMemberCart(me.id()).getId();
        }
        String token = cookies.readCartGuest(request);
        return cartService.findGuestCart(token).map(Cart::getId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니가 비어 있습니다."));
    }
}
