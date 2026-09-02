package com.rizenfood.api.cart;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.cart.dto.CartDtos;
import com.rizenfood.api.security.AuthCookies;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 장바구니 API.
 *
 * 회원·비회원 모두 쓴다 (2026-08-27 확정). 그래서 이 경로는 인증을 요구하지 않고
 * (SecurityConfig 에서 permitAll), 컨트롤러가 요청마다 주체를 가려낸다.
 *   - 회원 토큰이 있으면  → 회원 장바구니
 *   - 없으면              → 게스트 장바구니 (HttpOnly 쿠키의 추측불가 토큰으로 식별)
 *
 * 회원이 게스트 쿠키를 아직 들고 있으면, 접근하는 순간 게스트 장바구니를
 * 회원 장바구니에 병합하고 게스트 쿠키를 지운다.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService service;
    private final AuthCookies cookies;

    public CartController(CartService service, AuthCookies cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @GetMapping
    public CartDtos.CartView view(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request,
            HttpServletResponse response) {
        Cart cart = resolve(me, request, response);
        return service.view(cart.getId());
    }

    @PostMapping("/items")
    public CartDtos.CartView add(
            @Valid @RequestBody CartDtos.AddRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request,
            HttpServletResponse response) {
        Cart cart = resolve(me, request, response);
        service.add(cart.getId(), req);
        return service.view(cart.getId());
    }

    @PatchMapping("/items/{id}")
    public CartDtos.CartView updateQuantity(
            @PathVariable Long id,
            @Valid @RequestBody CartDtos.UpdateQtyRequest req,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request,
            HttpServletResponse response) {
        Cart cart = resolve(me, request, response);
        service.updateQuantity(cart.getId(), id, req.quantity());
        return service.view(cart.getId());
    }

    @DeleteMapping("/items/{id}")
    public CartDtos.CartView remove(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request,
            HttpServletResponse response) {
        Cart cart = resolve(me, request, response);
        service.remove(cart.getId(), id);
        return service.view(cart.getId());
    }

    @DeleteMapping
    public CartDtos.CartView clear(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            HttpServletRequest request,
            HttpServletResponse response) {
        Cart cart = resolve(me, request, response);
        service.clear(cart.getId());
        return service.view(cart.getId());
    }

    // ── 주체 판별 ─────────────────────────────────────────────

    /**
     * 이번 요청의 장바구니를 정한다. 필요하면 응답에 쿠키를 심는다.
     *
     * 회원이면 게스트 쿠키를 병합·소거하고 회원 장바구니를 준다.
     * 게스트면 쿠키의 토큰으로 장바구니를 찾고, 없으면 새 토큰으로 만들어 쿠키를 심는다.
     */
    private Cart resolve(JwtTokenProvider.AuthenticatedMember me,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        String guestToken = cookies.readCartGuest(request);

        if (me != null) {
            if (guestToken != null && !guestToken.isBlank()) {
                service.mergeGuestInto(guestToken, me.id());
                addCookie(response, cookies.expiredCartGuest());
            }
            return service.resolveMemberCart(me.id());
        }

        // 게스트. 토큰이 없으면 새로 발급한다.
        boolean needsNewToken = (guestToken == null || guestToken.isBlank());
        String tokenToUse = needsNewToken ? UUID.randomUUID().toString() : guestToken;
        Cart cart = service.resolveGuestCart(guestToken, tokenToUse);

        // 토큰이 없었거나, 쿠키의 토큰으로 장바구니를 못 찾아 새로 만든 경우
        // 응답 쿠키를 실제로 만들어진 장바구니의 토큰으로 맞춘다.
        if (needsNewToken || !cart.getGuestToken().equals(guestToken)) {
            addCookie(response, cookies.cartGuest(cart.getGuestToken()));
        }
        return cart;
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
