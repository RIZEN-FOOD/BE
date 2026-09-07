package com.rizenfood.api.member;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.member.dto.MemberAddressDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.validation.Valid;

/**
 * 회원 배송지 주소록.
 *
 * ★ 로그인한 회원 본인의 주소만 다룬다. 모든 조회·수정은 현재 회원 id 로 좁힌다.
 */
@RestController
@RequestMapping("/api/member/addresses")
@PreAuthorize("hasRole('MEMBER')")
public class MemberAddressController {

    private final MemberAddressService service;

    public MemberAddressController(MemberAddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberAddressDtos.Response> list(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {
        return service.list(me.id());
    }

    @PostMapping
    public MemberAddressDtos.Response create(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @Valid @RequestBody MemberAddressDtos.SaveRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public MemberAddressDtos.Response update(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @PathVariable Long id,
            @Valid @RequestBody MemberAddressDtos.SaveRequest req) {
        return service.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @PathVariable Long id) {
        service.delete(me.id(), id);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Map<String, String>> setDefault(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @PathVariable Long id) {
        service.setDefault(me.id(), id);
        return ResponseEntity.ok(Map.of("message", "기본 배송지로 설정했습니다."));
    }
}
