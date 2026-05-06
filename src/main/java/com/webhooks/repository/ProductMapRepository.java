package com.webhooks.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.webhooks.domain.ProductMap;

public interface ProductMapRepository extends JpaRepository<ProductMap, Long> {

    Optional<ProductMap> findBySku(String sku);

    void deleteBySiteAProductId(long siteAProductId);

    void deleteBySiteAParentId(long siteAParentId);
}
