package com.rizenfood.api.product;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * 그 밖의 법정 표시사항.
 *
 * 식품을 온라인에서 팔 때는 소비자가 구매 전에 이 정보를 볼 수 있어야 한다.
 * 전부 텍스트로 렌더한다. 이미지 안의 글자는 검색에도 안 잡히고 접근성에도 불리하다.
 */
@Entity
@Table(name = "product_label")
public class ProductLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "food_type", length = 120) private String foodType;
    @Column(name = "shelf_life", length = 200) private String shelfLife;
    @Column(name = "storage_method", length = 300) private String storageMethod;
    @Column(length = 200) private String manufacturer;
    @Column(name = "manufacturer_addr", length = 300) private String manufacturerAddr;
    @Column(length = 200) private String seller;
    @Column(name = "seller_addr", length = 300) private String sellerAddr;
    @Column(name = "customer_service", length = 120) private String customerService;
    @Column(name = "package_material", length = 200) private String packageMaterial;
    @Column(name = "extra_notice", columnDefinition = "text") private String extraNotice;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductLabel() {
    }

    void assignTo(Product product) {
        this.product = product;
    }

    public Long getId() { return id; }
    public String getFoodType() { return foodType; }
    public String getShelfLife() { return shelfLife; }
    public String getStorageMethod() { return storageMethod; }
    public String getManufacturer() { return manufacturer; }
    public String getManufacturerAddr() { return manufacturerAddr; }
    public String getSeller() { return seller; }
    public String getSellerAddr() { return sellerAddr; }
    public String getCustomerService() { return customerService; }
    public String getPackageMaterial() { return packageMaterial; }
    public String getExtraNotice() { return extraNotice; }

    public void setFoodType(String v) { this.foodType = v; }
    public void setShelfLife(String v) { this.shelfLife = v; }
    public void setStorageMethod(String v) { this.storageMethod = v; }
    public void setManufacturer(String v) { this.manufacturer = v; }
    public void setManufacturerAddr(String v) { this.manufacturerAddr = v; }
    public void setSeller(String v) { this.seller = v; }
    public void setSellerAddr(String v) { this.sellerAddr = v; }
    public void setCustomerService(String v) { this.customerService = v; }
    public void setPackageMaterial(String v) { this.packageMaterial = v; }
    public void setExtraNotice(String v) { this.extraNotice = v; }
}
