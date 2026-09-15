package com.rizenfood.api.member;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원 배송지.
 *
 * ★ 휴대폰(receiverPhoneEncrypted)은 암호화해 저장한다 (CLAUDE.md 규칙 6).
 *   서비스가 PhoneCipher 로 넣고 뺀다. 엔티티는 암호문만 들고 있다.
 * ★ 기본 배송지는 회원당 하나. DB 의 부분 유니크 인덱스가 이를 보장하고,
 *   서비스가 새 기본을 지정할 때 기존 기본을 먼저 내린다.
 */
@Entity
@Table(name = "member_address")
public class MemberAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(length = 50)
    private String label;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone_encrypted", length = 500)
    private String receiverPhoneEncrypted;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 300)
    private String addr1;

    @Column(length = 300)
    private String addr2;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MemberAddress() {
    }

    public static MemberAddress create(Long memberId) {
        MemberAddress a = new MemberAddress();
        a.memberId = memberId;
        return a;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getLabel() { return label; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhoneEncrypted() { return receiverPhoneEncrypted; }
    public String getZipcode() { return zipcode; }
    public String getAddr1() { return addr1; }
    public String getAddr2() { return addr2; }
    public boolean isDefault() { return isDefault; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLabel(String label) { this.label = label; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public void setReceiverPhoneEncrypted(String v) { this.receiverPhoneEncrypted = v; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }
    public void setAddr1(String addr1) { this.addr1 = addr1; }
    public void setAddr2(String addr2) { this.addr2 = addr2; }
    public void setDefault(boolean value) { this.isDefault = value; }
}
