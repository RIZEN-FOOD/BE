package com.rizenfood.api.coupon;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 할인코드 한 장.
 *
 * 관리자가 코드·할인율·기간을 정해 발행하고, 손님이 결제 화면에서 직접 입력한다.
 * 구조는 V9 에서 세워둔 coupon 테이블 그대로다 (컬럼을 더하지 않고 그 안에서 해결한다).
 *
 * ★ 금액 계산은 전부 서버가 한다. 화면이 보낸 할인액은 쓰지 않는다 (CLAUDE.md 규칙 5).
 * ★ 남은 수량은 used_count 를 원자적으로 올려서 막는다. 읽고-나서-쓰기를 하지 않는다.
 */
@Entity
@Table(name = "coupon")
public class Coupon {

    public static final String PERCENT = "PERCENT";
    public static final String AMOUNT = "AMOUNT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false, length = 200)
    private String name;

    /** 손님이 입력하는 코드. 대문자로 저장한다 (입력은 대소문자를 가리지 않는다). */
    @Column(length = 60)
    private String code;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType = PERCENT;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    /** 정률 할인의 상한. NULL 이면 상한 없음. */
    @Column(name = "max_discount")
    private Integer maxDiscount;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount = 0;

    /** 전체 사용 한도. NULL 이면 무제한. */
    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount = 0;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "per_member_limit", nullable = false)
    private int perMemberLimit = 1;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "valid_days")
    private Integer validDays;

    /** 켜짐/꺼짐. 꺼두면 기간 안이라도 쓸 수 없다. */
    @Column(nullable = false)
    private boolean visible = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // ── 규칙 ──────────────────────────────────────────────────

    /** 지금 쓸 수 있는 상태인가 (켜짐 + 기간 안). 수량·1인한도는 서비스가 본다. */
    public boolean isOpenAt(Instant now) {
        return visible && startAt != null && endAt != null
                && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    /**
     * 상품 금액에 대한 할인액. 배송비는 깎지 않는다.
     * 정률이면 내림으로 원 단위를 맞추고, 상한이 있으면 거기서 자른다.
     * 어떤 경우에도 상품 금액을 넘지 않는다 (총액이 음수가 될 수 없다).
     */
    public int discountFor(int itemsAmount) {
        if (itemsAmount <= 0) return 0;
        int raw = PERCENT.equals(discountType)
                ? (int) ((long) itemsAmount * discountValue / 100)
                : discountValue;
        if (maxDiscount != null && raw > maxDiscount) {
            raw = maxDiscount;
        }
        return Math.min(raw, itemsAmount);
    }

    public boolean meetsMinimum(int itemsAmount) {
        return itemsAmount >= minOrderAmount;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    // ── getter / setter ───────────────────────────────────────

    public Long getId() { return id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long v) { this.eventId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String v) { this.discountType = v; }

    public int getDiscountValue() { return discountValue; }
    public void setDiscountValue(int v) { this.discountValue = v; }

    public Integer getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(Integer v) { this.maxDiscount = v; }

    public int getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(int v) { this.minOrderAmount = v; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer v) { this.totalQuantity = v; }

    public int getIssuedCount() { return issuedCount; }

    public int getUsedCount() { return usedCount; }

    public int getPerMemberLimit() { return perMemberLimit; }
    public void setPerMemberLimit(int v) { this.perMemberLimit = v; }

    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant v) { this.startAt = v; }

    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant v) { this.endAt = v; }

    public Integer getValidDays() { return validDays; }
    public void setValidDays(Integer v) { this.validDays = v; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
