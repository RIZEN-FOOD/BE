package com.rizenfood.api.inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Page<Inquiry> findByMemberId(Long memberId, Pageable pageable);

    Page<Inquiry> findByStatus(String status, Pageable pageable);

    boolean existsByIdAndMemberId(Long id, Long memberId);
}
