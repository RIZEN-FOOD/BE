package com.rizenfood.api.notice;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rizenfood.api.notice.dto.NoticeDtos;

/**
 * 공개 공지 API. 인증이 필요 없다.
 * 발행된 공지만 나간다.
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private static final int MAX_SIZE = 50;

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {

        Page<NoticeDtos.PublicListItem> result = service.listPublic(
                keyword, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE)));

        return Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "totalPages", result.getTotalPages(),
                "totalCount", result.getTotalElements());
    }

    @GetMapping("/{id}")
    public NoticeDtos.PublicDetail detail(@PathVariable Long id) {
        return service.getPublic(id);
    }
}
