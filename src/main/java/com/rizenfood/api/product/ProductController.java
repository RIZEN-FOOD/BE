package com.rizenfood.api.product;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.product.dto.ProductDtos;

/**
 * 공개 상품 API. 인증이 필요 없다.
 * visible=true 인 것만 나간다. 숨긴 상품은 목록에도 상세에도 없다.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** 페이지 크기 상한. 한 번에 전부 긁어가는 것을 막는다. */
    private static final int MAX_SIZE = 60;

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** @param sort new | price-asc | price-desc */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "new") String sort) {

        Page<ProductDtos.ListItem> result =
                service.listPublic(PageRequest.of(Math.max(0, page), clamp(size), sortOf(sort)));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    /** 메인 페이지의 상품 그리드. isFeatured 인 것만. */
    @GetMapping("/featured")
    public List<ProductDtos.ListItem> featured() {
        return service.listFeatured();
    }

    @GetMapping("/{slug}")
    public ProductDtos.Detail detail(@PathVariable String slug) {
        return service.getPublic(slug);
    }

    private int clamp(int size) {
        return Math.min(Math.max(1, size), MAX_SIZE);
    }

    /**
     * 정렬 기준을 화이트리스트로 받는다.
     * 클라이언트가 준 문자열을 그대로 Sort 에 넣으면 임의 컬럼으로 정렬하며
     * 내부 구조를 탐색할 수 있다.
     */
    private Sort sortOf(String sort) {
        return switch (sort) {
            case "price-asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "price");
            // 기본은 관리자가 정한 순서, 그다음 최신순
            default -> Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"));
        };
    }
}
