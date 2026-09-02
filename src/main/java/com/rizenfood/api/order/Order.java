package com.rizenfood.api.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * 주문.
 *
 * ★ 금액은 절대 클라이언트가 보낸 값을 쓰지 않는다. 서버가 상품 테이블을 다시 읽어
 *   계산한 결과만 여기 남는다 (CLAUDE.md 규칙 5). DB CHECK 가 합계 정합성을 강제한다.
 *
 * ★ 배송지·수령인·상품명·가격은 주문 시점 값으로 박아둔다(스냅샷). 회원이 주소를
 *   바꾸거나 상품이 수정·삭제돼도 이 주문 기록은 그대로여야 한다.
 *
 * ★ 주문번호(orderNo)는 추측 불가능해야 한다. 순번을 쓰면 남의 주문을 훑을 수 있다.
 *
 * 휴대폰 번호는 애플리케이션에서 암호화해 저장한다(_encrypted).
 */
@Entity
@Table(name = "orders")
public class Order {

    public enum Status { PENDING, PAID, PREPARING, SHIPPED, DELIVERED, CANCELLED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    /** 비회원 주문이면 null. 회원이 탈퇴해도 주문은 남는다(스키마 SET NULL). */
    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false, length = 20)
    private String status = Status.PENDING.name();

    @Column(name = "orderer_name", nullable = false, length = 100)
    private String ordererName;

    @Column(name = "orderer_phone_encrypted", nullable = false, length = 500)
    private String ordererPhoneEncrypted;

    @Column(name = "orderer_email", length = 320)
    private String ordererEmail;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone_encrypted", nullable = false, length = 500)
    private String receiverPhoneEncrypted;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 300)
    private String addr1;

    @Column(length = 300)
    private String addr2;

    @Column(name = "delivery_memo", length = 300)
    private String deliveryMemo;

    @Column(name = "items_amount", nullable = false)
    private int itemsAmount;

    @Column(name = "shipping_fee", nullable = false)
    private int shippingFee;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "ordered_at", nullable = false, insertable = false, updatable = false)
    private Instant orderedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.attachTo(this);
    }

    public void markPaid() {
        this.status = Status.PAID.name();
        this.paidAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED.name();
        this.cancelledAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isPending() {
        return Status.PENDING.name().equals(status);
    }

    // ── getters/setters (필요한 것만) ──
    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getStatus() { return status; }
    public String getOrdererName() { return ordererName; }
    public void setOrdererName(String v) { this.ordererName = v; }
    public String getOrdererPhoneEncrypted() { return ordererPhoneEncrypted; }
    public void setOrdererPhoneEncrypted(String v) { this.ordererPhoneEncrypted = v; }
    public String getOrdererEmail() { return ordererEmail; }
    public void setOrdererEmail(String v) { this.ordererEmail = v; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String v) { this.receiverName = v; }
    public String getReceiverPhoneEncrypted() { return receiverPhoneEncrypted; }
    public void setReceiverPhoneEncrypted(String v) { this.receiverPhoneEncrypted = v; }
    public String getZipcode() { return zipcode; }
    public void setZipcode(String v) { this.zipcode = v; }
    public String getAddr1() { return addr1; }
    public void setAddr1(String v) { this.addr1 = v; }
    public String getAddr2() { return addr2; }
    public void setAddr2(String v) { this.addr2 = v; }
    public String getDeliveryMemo() { return deliveryMemo; }
    public void setDeliveryMemo(String v) { this.deliveryMemo = v; }
    public int getItemsAmount() { return itemsAmount; }
    public void setItemsAmount(int v) { this.itemsAmount = v; }
    public int getShippingFee() { return shippingFee; }
    public void setShippingFee(int v) { this.shippingFee = v; }
    public int getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(int v) { this.discountAmount = v; }
    public int getTotalAmount() { return totalAmount; }
    public void setTotalAmount(int v) { this.totalAmount = v; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getPaidAt() { return paidAt; }
    public List<OrderItem> getItems() { return items; }
}
