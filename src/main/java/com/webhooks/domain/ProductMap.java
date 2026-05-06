package com.webhooks.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_map")
public class ProductMap {

    @Id
    @Column(name = "site_a_product_id")
    private long siteAProductId;

    @Column(name = "site_b_product_id", nullable = false)
    private long siteBProductId;

    @Column(name = "sku", length = 255)
    private String sku;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType;

    @Column(name = "site_a_parent_id")
    private Long siteAParentId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public long getSiteAProductId() {
        return siteAProductId;
    }

    public void setSiteAProductId(long siteAProductId) {
        this.siteAProductId = siteAProductId;
    }

    public long getSiteBProductId() {
        return siteBProductId;
    }

    public void setSiteBProductId(long siteBProductId) {
        this.siteBProductId = siteBProductId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Long getSiteAParentId() {
        return siteAParentId;
    }

    public void setSiteAParentId(Long siteAParentId) {
        this.siteAParentId = siteAParentId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
