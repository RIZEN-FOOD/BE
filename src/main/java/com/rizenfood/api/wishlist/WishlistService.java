package com.rizenfood.api.wishlist;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.product.Product;
import com.rizenfood.api.product.ProductRepository;
import com.rizenfood.api.wishlist.dto.WishlistDtos;

/**
 * 위시리스트(찜). 회원 전용.
 * 담기는 멱등하게 처리한다 — 이미 있으면 그대로 둔다.
 */
@Service
public class WishlistService {

    private final WishlistRepository repository;
    private final ProductRepository productRepository;
    private final ImageService imageService;

    public WishlistService(WishlistRepository repository, ProductRepository productRepository,
                           ImageService imageService) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.imageService = imageService;
    }

    @Transactional
    public void add(Long memberId, Long productId) {
        if (repository.existsByMemberIdAndProductId(memberId, productId)) {
            return; // 이미 담김. 멱등.
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다."));
        repository.save(new WishlistItem(memberId, product));
    }

    @Transactional
    public void remove(Long memberId, Long productId) {
        repository.deleteByMemberIdAndProductId(memberId, productId);
    }

    @Transactional(readOnly = true)
    public List<WishlistDtos.Item> list(Long memberId) {
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(w -> {
                    Product p = w.getProduct();
                    int effective = p.getDiscountPrice() != null ? p.getDiscountPrice() : p.getPrice();
                    boolean soldOut = !p.isVisible() || p.getStock() <= 0;
                    return new WishlistDtos.Item(
                            p.getId(), p.getSlug(), p.getNameKo(), effective,
                            thumb(p.getThumbnailKey()), soldOut, w.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> productIds(Long memberId) {
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(w -> w.getProduct().getId()).toList();
    }

    private String thumb(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return imageService.urlOf(key + "_thumb.webp");
    }
}
