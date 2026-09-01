package com.rizenfood.api.review;

import java.io.IOException;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rizenfood.api.image.ImageService;
import com.rizenfood.api.image.ImageValidationException;
import com.rizenfood.api.image.ProcessedImage;
import com.rizenfood.api.review.dto.ReviewDtos;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.validation.Valid;

/**
 * 로그인한 회원의 후기 작성·조회·삭제.
 * 사진 업로드는 관리자 업로드와 같은 파이프라인(검증·재인코딩·리사이즈)을 탄다.
 */
@RestController
@RequestMapping("/api/member/reviews")
@PreAuthorize("hasRole('MEMBER')")
public class MemberReviewController {

    private final ReviewService service;
    private final ImageService imageService;

    public MemberReviewController(ReviewService service, ImageService imageService) {
        this.service = service;
        this.imageService = imageService;
    }

    @GetMapping
    public Map<String, Object> mine(
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ReviewDtos.Item> result = service.listMine(me.id(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 30),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(
            @Valid @RequestBody ReviewDtos.CreateRequest request,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {

        Long id = service.create(me.id(), request);
        return ResponseEntity.status(201).body(Map.of("id", id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedMember me) {

        service.deleteMine(me.id(), id);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    /** 후기 사진 업로드. 키를 받아 CreateRequest.imageKeys 에 넣어 다시 보낸다. */
    @PostMapping("/images")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException("파일을 읽지 못했습니다. 다시 시도해 주세요.");
        }

        ProcessedImage processed = imageService.upload(
                bytes, file.getOriginalFilename(), file.getContentType(), "reviews");

        return ResponseEntity.ok(Map.of(
                "key", processed.key(),
                "url", imageService.urlOf(processed.key() + "_medium.webp")));
    }
}
