package com.rizenfood.api.review;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.review.dto.ReviewDtos;

/**
 * 공개 후기 API. 인증이 필요 없다.
 *
 * productId 없이 부르면 사이트 전체 후기(모아보기 페이지),
 * productId 를 주면 그 상품의 후기만(상품 상세 탭) 나간다.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final int MAX_SIZE = 50;

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<ReviewDtos.Item> result = service.listPublic(productId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    /** 별점 평균·개수. 상품 상세 페이지의 별점 요약에 쓴다. */
    @GetMapping("/stats")
    public ReviewDtos.Stats stats(@RequestParam Long productId) {
        return service.stats(productId);
    }
}
