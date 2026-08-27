package com.rizenfood.api.image;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.rizenfood.api.audit.AuditService;
import com.rizenfood.api.security.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 이미지 업로드.
 *
 * 모든 관리 API 에 @PreAuthorize 가 붙어야 한다. 하나만 빠져도 구멍이다 (기획서 §10).
 */
@RestController
@RequestMapping("/api/admin/images")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminImageController {

    private final ImageService imageService;
    private final AuditService auditService;

    public AdminImageController(ImageService imageService, AuditService auditService) {
        this.imageService = imageService;
        this.auditService = auditService;
    }

    /**
     * @param file     업로드 파일
     * @param category 저장 경로 앞부분 (products, banners, notices, reviews)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "products") String category,
            @AuthenticationPrincipal JwtTokenProvider.AuthenticatedAdmin admin,
            HttpServletRequest request) {

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException("파일을 읽지 못했습니다. 다시 시도해 주세요.");
        }

        ProcessedImage processed = imageService.upload(
                bytes, file.getOriginalFilename(), file.getContentType(), category);

        auditService.record(admin.id(), admin.displayName(), "UPLOAD_IMAGE",
                "IMAGE", processed.key(), category, request);

        // 크기별 URL 을 함께 준다. 관리자 화면이 미리보기에 바로 쓴다.
        Map<String, String> urls = new LinkedHashMap<>();
        processed.variants().forEach((variant, key) ->
                urls.put(variant.suffix(), imageService.urlOf(key)));

        Map<String, String> keys = new LinkedHashMap<>();
        processed.variants().forEach((variant, key) -> keys.put(variant.suffix(), key));

        return ResponseEntity.ok(Map.of(
                "key", processed.key(),
                "width", processed.width(),
                "height", processed.height(),
                "keys", keys,
                "urls", urls));
    }
}
