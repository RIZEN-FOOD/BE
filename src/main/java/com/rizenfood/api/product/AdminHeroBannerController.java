package com.rizenfood.api.product;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.product.dto.ProductDtos;

import jakarta.validation.Valid;

/**
 * 관리자 메인 히어로 배너 API.
 *
 * 배너는 상품(product)의 히어로 필드로 저장된다 — 상품 1개 = 슬라이드 1개.
 * 이미지·색·문구·구성 이미지 4종·표시 순서·노출 여부만 다룬다.
 * 가격·재고·품절은 상품 API 에서 관리한다.
 *
 * 클래스 단위 @PreAuthorize 로 권한 누락을 막는다 (기획서 §10).
 */
@RestController
@RequestMapping("/api/admin/hero-banners")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminHeroBannerController {

    private final ProductService service;

    public AdminHeroBannerController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductDtos.HeroBannerRow> list() {
        return service.listHeroBanners();
    }

    @GetMapping("/{id}")
    public ProductDtos.HeroBannerDetail get(@PathVariable Long id) {
        return service.getHeroBanner(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> save(@PathVariable Long id,
                                     @Valid @RequestBody ProductDtos.HeroBannerSaveRequest request) {
        service.saveHeroBanner(id, request);
        return ResponseEntity.noContent().build();
    }
}
