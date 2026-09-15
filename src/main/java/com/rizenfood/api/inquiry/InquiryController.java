package com.rizenfood.api.inquiry;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.inquiry.dto.InquiryDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.validation.Valid;

/**
 * 문의 접수. 회원·비회원 모두 쓸 수 있다.
 *
 * 인증이 없어도 되는 공개 엔드포인트지만, 로그인한 상태로 호출하면
 * JwtAuthenticationFilter 가 심어둔 인증 정보를 받아 member_id 를 채운다.
 * 로그인 없이 불러도 그대로 접수된다.
 */
@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryService service;

    public InquiryController(InquiryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            @Valid @RequestBody InquiryDtos.CreateRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {

        Long memberId = me == null ? null : me.id();
        Long id = service.create(memberId, request);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }
}
