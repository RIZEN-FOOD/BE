package com.rizenfood.api.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.inquiry.dto.InquiryDtos;
import com.rizenfood.api.member.PhoneCipher;

/**
 * 문의 접수·조회·답변.
 *
 * 회원이면 memberId 가 채워지고, 비회원이면 null 이다.
 * 어느 쪽이든 이름·이메일은 매번 직접 받는다 — 회원이어도 문의마다
 * 다른 연락처를 남기고 싶을 수 있고, 서버가 세션에서 조용히 채워넣지 않는다.
 */
@Service
public class InquiryService {

    private final InquiryRepository repository;
    private final PhoneCipher phoneCipher;

    public InquiryService(InquiryRepository repository, PhoneCipher phoneCipher) {
        this.repository = repository;
        this.phoneCipher = phoneCipher;
    }

    @Transactional
    public Long create(Long memberId, InquiryDtos.CreateRequest request) {
        String phoneEnc = (request.phone() == null || request.phone().isBlank())
                ? null
                : phoneCipher.encrypt(request.phone().replaceAll("[^0-9]", ""));

        Inquiry inquiry = new Inquiry(
                memberId,
                request.type() == null ? "GENERAL" : request.type(),
                request.name().trim(), request.email().trim(), phoneEnc, request.message());

        return repository.save(inquiry).getId();
    }

    @Transactional(readOnly = true)
    public Page<InquiryDtos.Item> listMine(Long memberId, Pageable pageable) {
        return repository.findByMemberId(memberId, pageable).map(this::toItem);
    }

    @Transactional(readOnly = true)
    public Page<InquiryDtos.AdminItem> listForAdmin(String status, Pageable pageable) {
        Page<Inquiry> page = (status == null || status.isBlank())
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return page.map(this::toAdminItem);
    }

    @Transactional
    public void answer(Long id, String answerText) {
        Inquiry inquiry = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("문의를 찾을 수 없습니다."));
        inquiry.answer(answerText);
    }

    @Transactional
    public void close(Long id) {
        Inquiry inquiry = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("문의를 찾을 수 없습니다."));
        inquiry.close();
    }

    private InquiryDtos.Item toItem(Inquiry i) {
        return new InquiryDtos.Item(
                i.getId(), i.getType(), i.getName(), i.getMessage(),
                i.getAnswer(), i.getAnsweredAt(), i.getStatus(), i.getCreatedAt());
    }

    private InquiryDtos.AdminItem toAdminItem(Inquiry i) {
        return new InquiryDtos.AdminItem(
                i.getId(), i.getType(), i.getName(), i.getEmail(), i.getMessage(),
                i.getAnswer(), i.getAnsweredAt(), i.getStatus(), i.getCreatedAt());
    }
}
