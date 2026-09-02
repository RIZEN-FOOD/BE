package com.rizenfood.api.cart;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 장바구니.
 *
 * 회원 장바구니이거나(memberId) 게스트 장바구니이거나(guestToken) 둘 중 하나다.
 * 스키마의 CHECK 제약이 "정확히 하나"를 강제한다.
 *
 * ★ 비회원도 담을 수 있다 (2026-08-27 확정). 게스트는 추측 불가능한 토큰으로
 *   식별하고, 그 토큰은 HttpOnly 쿠키로만 오간다. 로그인하면 게스트 장바구니를
 *   회원 장바구니에 병합한 뒤 게스트 행을 지운다.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "guest_token", length = 64)
    private String guestToken;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public static Cart forMember(Long memberId) {
        Cart c = new Cart();
        c.memberId = memberId;
        return c;
    }

    public static Cart forGuest(String guestToken) {
        Cart c = new Cart();
        c.guestToken = guestToken;
        return c;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getGuestToken() {
        return guestToken;
    }

    public List<CartItem> getItems() {
        return items;
    }
}
