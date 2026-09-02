package com.rizenfood.api.order;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.order.dto.OrderDtos;
import com.rizenfood.api.security.JwtTokenProvider;

/**
 * 로그인한 회원의 주문 목록(마이페이지).
 * 개별 주문 상세·결제는 공용 /api/orders 를 쓴다.
 */
@RestController
@RequestMapping("/api/member/orders")
@PreAuthorize("hasRole('MEMBER')")
public class MemberOrderController {

    private final OrderService orderService;

    public MemberOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Map<String, Object> mine(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<OrderDtos.OrderSummary> result = orderService.listMine(me.id(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 30)));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }
}
