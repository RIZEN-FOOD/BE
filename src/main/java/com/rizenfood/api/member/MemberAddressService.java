package com.rizenfood.api.member;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rizenfood.api.common.NotFoundException;
import com.rizenfood.api.member.dto.MemberAddressDtos;

/**
 * 회원 배송지 주소록.
 *
 * ★ 항상 memberId 로 좁혀 남의 주소를 만지지 못하게 한다.
 * ★ 휴대폰은 PhoneCipher 로 암호화해 저장하고, 본인에게 돌려줄 때만 복호화한다 (규칙 6).
 * ★ 기본 배송지는 회원당 하나. 새 기본을 지정할 때 기존 기본을 먼저 내린다.
 *   첫 주소는 자동으로 기본이 된다.
 */
@Service
public class MemberAddressService {

    /** 회원당 최대 저장 개수. */
    private static final int MAX_ADDRESSES = 10;

    private final MemberAddressRepository repo;
    private final PhoneCipher phoneCipher;

    public MemberAddressService(MemberAddressRepository repo, PhoneCipher phoneCipher) {
        this.repo = repo;
        this.phoneCipher = phoneCipher;
    }

    @Transactional(readOnly = true)
    public List<MemberAddressDtos.Response> list(Long memberId) {
        return repo.findByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public MemberAddressDtos.Response create(Long memberId, MemberAddressDtos.SaveRequest req) {
        if (repo.countByMemberId(memberId) >= MAX_ADDRESSES) {
            throw new IllegalArgumentException("배송지는 최대 " + MAX_ADDRESSES + "개까지 저장할 수 있습니다.");
        }

        MemberAddress a = MemberAddress.create(memberId);
        apply(a, req);

        // 첫 주소이거나 기본 지정을 요청하면 기본으로 만든다.
        boolean makeDefault = req.makeDefault() || !repo.existsByMemberIdAndIsDefaultTrue(memberId);
        if (makeDefault) {
            repo.clearDefault(memberId);
            a.setDefault(true);
        }
        return toResponse(repo.save(a));
    }

    @Transactional
    public MemberAddressDtos.Response update(Long memberId, Long id, MemberAddressDtos.SaveRequest req) {
        MemberAddress a = repo.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new NotFoundException("배송지를 찾을 수 없습니다."));
        apply(a, req);

        if (req.makeDefault() && !a.isDefault()) {
            repo.clearDefault(memberId);
            a.setDefault(true);
        }
        a.touch();
        return toResponse(a);
    }

    @Transactional
    public void delete(Long memberId, Long id) {
        MemberAddress a = repo.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new NotFoundException("배송지를 찾을 수 없습니다."));
        boolean wasDefault = a.isDefault();
        repo.delete(a);

        // 기본 배송지를 지웠으면 남은 것 중 가장 최근 것을 기본으로 올린다.
        if (wasDefault) {
            repo.findByMemberIdOrderByIsDefaultDescCreatedAtDesc(memberId)
                    .stream().findFirst().ifPresent((next) -> next.setDefault(true));
        }
    }

    @Transactional
    public void setDefault(Long memberId, Long id) {
        MemberAddress a = repo.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new NotFoundException("배송지를 찾을 수 없습니다."));
        if (!a.isDefault()) {
            repo.clearDefault(memberId);
            a.setDefault(true);
        }
    }

    // ── 내부 ──────────────────────────────────────────

    private void apply(MemberAddress a, MemberAddressDtos.SaveRequest req) {
        a.setLabel(blankToNull(req.label()));
        a.setReceiverName(req.receiverName().trim());
        String phone = blankToNull(req.receiverPhone());
        a.setReceiverPhoneEncrypted(phone == null ? null : phoneCipher.encrypt(phone));
        a.setZipcode(req.zipcode().trim());
        a.setAddr1(req.addr1().trim());
        a.setAddr2(blankToNull(req.addr2()));
    }

    private MemberAddressDtos.Response toResponse(MemberAddress a) {
        return new MemberAddressDtos.Response(
                a.getId(), a.getLabel(), a.getReceiverName(),
                phoneCipher.decrypt(a.getReceiverPhoneEncrypted()),
                a.getZipcode(), a.getAddr1(), a.getAddr2(), a.isDefault());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
