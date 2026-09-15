package com.rizenfood.api.wishlist;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.security.JwtTokenProvider;
import com.rizenfood.api.wishlist.dto.WishlistDtos;

/**
 * 위시리스트(찜). 회원 전용.
 */
@RestController
@RequestMapping("/api/member/wishlist")
@PreAuthorize("hasRole('MEMBER')")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    /** 전체 목록. */
    @GetMapping
    public List<WishlistDtos.Item> list(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        return service.list(me.id());
    }

    /** 찜한 상품 id 목록. 상품 목록·상세에서 하트 상태 표시에 쓴다. */
    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        return service.productIds(me.id());
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, String>> add(
            @PathVariable Long productId,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        service.add(me.id(), productId);
        return ResponseEntity.ok(Map.of("message", "찜 목록에 담았습니다."));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, String>> remove(
            @PathVariable Long productId,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        service.remove(me.id(), productId);
        return ResponseEntity.ok(Map.of("message", "찜을 해제했습니다."));
    }
}
