package com.rizenfood.api.inquiry;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.inquiry.dto.InquiryDtos;
import com.rizenfood.api.security.JwtTokenProvider;

/** 내 문의 내역 (마이페이지) */
@RestController
@RequestMapping("/api/member/inquiries")
@PreAuthorize("hasRole('MEMBER')")
public class MemberInquiryController {

    private final InquiryService service;

    public MemberInquiryController(InquiryService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> mine(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<InquiryDtos.Item> result = service.listMine(me.id(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 30),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }
}
