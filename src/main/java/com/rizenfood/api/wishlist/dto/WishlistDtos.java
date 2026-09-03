package com.rizenfood.api.wishlist.dto;

import java.time.Instant;

public final class WishlistDtos {
    private WishlistDtos() {}

    public record Item(
            Long productId, String slug, String name, int price,
            String thumbnailUrl, boolean soldOut, Instant addedAt) {
    }
}
