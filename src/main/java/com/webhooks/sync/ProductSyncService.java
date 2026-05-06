package com.webhooks.sync;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.webhooks.config.WooCommerceProperties;
import com.webhooks.domain.ProductMap;
import com.webhooks.domain.SyncJob;
import com.webhooks.repository.ProductMapRepository;
import com.webhooks.woocommerce.WooCommerceSiteAReader;
import com.webhooks.woocommerce.WooCommerceSiteBApi;
import com.webhooks.woocommerce.WooCommerceSyncException;

@Service
public class ProductSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncService.class);

    private final ObjectMapper objectMapper;
    private final WooCommerceProperties props;
    private final WooCommerceSiteAReader siteA;
    private final WooCommerceSiteBApi siteB;
    private final ProductJsonCleaner cleaner;
    private final ProductMapRepository productMapRepository;

    public ProductSyncService(
        ObjectMapper objectMapper,
        WooCommerceProperties props,
        WooCommerceSiteAReader siteA,
        WooCommerceSiteBApi siteB,
        ProductJsonCleaner cleaner,
        ProductMapRepository productMapRepository
    ) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.siteA = siteA;
        this.siteB = siteB;
        this.cleaner = cleaner;
        this.productMapRepository = productMapRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void perform(SyncJob job) {
        siteB.validateConfigured();
        JsonNode root;
        try {
            root = objectMapper.readTree(job.getPayload());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON payload", e);
        }

        String topic = job.getTopic() == null ? "" : job.getTopic();
        if (topic.contains("deleted")) {
            handleDelete(root);
            return;
        }

        JsonNode product = resolveFromSiteA(root);
        String type = text(product.get("type"));

        if ("variation".equals(type)) {
            long parentAid = product.get("parent_id").asLong();
            ProductMap parentRow = productMapRepository
                .findById(parentAid)
                .orElseThrow(() -> new TransientSyncException(
                    "Parent not on Site B yet for Site A parent id=" + parentAid + "; will retry."
                ));
            syncVariation(product, parentRow.getSiteBProductId(), parentAid);
            return;
        }

        if ("variable".equals(type)) {
            syncVariableProduct(product);
            return;
        }

        syncGenericProduct(product);
    }

    private JsonNode resolveFromSiteA(JsonNode webhookPayload) {
        if (!props.hasSourceCredentials() || !props.isSourceReadEnabled()) {
            return webhookPayload;
        }
        long id = webhookPayload.get("id").asLong();
        String type = text(webhookPayload.get("type"));
        try {
            if ("variation".equals(type) && webhookPayload.hasNonNull("parent_id")) {
                long pid = webhookPayload.get("parent_id").asLong();
                return siteA.getVariation(pid, id);
            }
            return siteA.getProduct(id);
        } catch (WooCommerceSyncException ex) {
            log.warn("Site A fetch failed ({}), using webhook body: {}", ex.getStatusCode(), ex.getMessage());
            return webhookPayload;
        } catch (Exception ex) {
            log.warn("Site A fetch failed, using webhook body: {}", ex.getMessage());
            return webhookPayload;
        }
    }

    private void handleDelete(JsonNode payload) {
        if (!payload.hasNonNull("id")) {
            return;
        }
        long siteAId = payload.get("id").asLong();
        Optional<ProductMap> rowOpt = productMapRepository.findById(siteAId);
        if (rowOpt.isEmpty()) {
            log.debug("No mapping for deleted Site A product {}, nothing to delete on B", siteAId);
            return;
        }
        ProductMap row = rowOpt.get();
        if ("variation".equals(row.getProductType()) && row.getSiteAParentId() != null) {
            Optional<ProductMap> parentRow = productMapRepository.findById(row.getSiteAParentId());
            if (parentRow.isPresent()) {
                siteB.deleteVariation(parentRow.get().getSiteBProductId(), row.getSiteBProductId());
            }
            productMapRepository.delete(row);
            return;
        }
        siteB.deleteProduct(row.getSiteBProductId());
        productMapRepository.deleteBySiteAParentId(siteAId);
        productMapRepository.delete(row);
    }

    private void syncGenericProduct(JsonNode p) {
        long siteAId = p.get("id").asLong();
        String sku = text(p.get("sku"));
        ObjectNode body = cleaner.forMirror(p, null, false);

        Optional<ProductMap> byAid = productMapRepository.findById(siteAId);
        if (byAid.isPresent()) {
            JsonNode out = siteB.updateProduct(byAid.get().getSiteBProductId(), body);
            saveMap(siteAId, out, sku, text(p.get("type")), null);
            return;
        }

        if (sku != null && !sku.isBlank()) {
            Optional<JsonNode> existing = siteB.findProductBySku(sku);
            if (existing.isPresent()) {
                long bid = existing.get().get("id").asLong();
                JsonNode out = siteB.updateProduct(bid, body);
                saveMap(siteAId, out, sku, text(p.get("type")), null);
                return;
            }
        }

        JsonNode out = siteB.createProduct(body);
        saveMap(siteAId, out, sku, text(p.get("type")), null);
    }

    private void syncVariableProduct(JsonNode parentFromA) {
        long parentAId = parentFromA.get("id").asLong();
        String parentSku = text(parentFromA.get("sku"));
        ObjectNode parentBody = cleaner.forMirror(parentFromA, null, true);
        long parentBId = ensureParentOnB(parentAId, parentSku, parentBody);

        JsonNode variationIds = parentFromA.get("variations");
        if (variationIds == null || !variationIds.isArray() || variationIds.isEmpty()) {
            return;
        }

        if (!props.hasSourceCredentials() || !props.isSourceReadEnabled()) {
            log.warn("Variable product {} has variations but Site A REST is disabled — skipping variation rows.", parentAId);
            return;
        }

        for (JsonNode vid : variationIds) {
            long varAId = vid.asLong();
            JsonNode varNode;
            try {
                varNode = siteA.getVariation(parentAId, varAId);
            } catch (Exception e) {
                log.warn("Could not load variation {} for parent {}: {}", varAId, parentAId, e.getMessage());
                continue;
            }
            syncVariation(varNode, parentBId, parentAId);
        }
    }

    private long ensureParentOnB(long parentAId, String parentSku, ObjectNode parentBody) {
        Optional<ProductMap> mp = productMapRepository.findById(parentAId);
        if (mp.isPresent()) {
            long bid = mp.get().getSiteBProductId();
            JsonNode out = siteB.updateProduct(bid, parentBody);
            saveMap(parentAId, out, parentSku, "variable", null);
            return bid;
        }
        if (parentSku != null && !parentSku.isBlank()) {
            Optional<JsonNode> bySku = siteB.findProductBySku(parentSku);
            if (bySku.isPresent()) {
                long bid = bySku.get().get("id").asLong();
                JsonNode out = siteB.updateProduct(bid, parentBody);
                saveMap(parentAId, out, parentSku, "variable", null);
                return bid;
            }
        }
        JsonNode out = siteB.createProduct(parentBody);
        long bid = out.get("id").asLong();
        saveMap(parentAId, out, parentSku, "variable", null);
        return bid;
    }

    private void syncVariation(JsonNode varFromA, long parentBId, long parentAId) {
        long varAId = varFromA.get("id").asLong();
        String sku = text(varFromA.get("sku"));
        ObjectNode body = cleaner.forMirror(varFromA, parentBId, false);

        Optional<ProductMap> vm = productMapRepository.findById(varAId);
        if (vm.isPresent()) {
            JsonNode out = siteB.updateVariation(parentBId, vm.get().getSiteBProductId(), body);
            saveMap(varAId, out, sku, "variation", parentAId);
            return;
        }

        Optional<Long> bySku = findVariationIdOnBBySku(parentBId, sku);
        if (bySku.isPresent()) {
            JsonNode out = siteB.updateVariation(parentBId, bySku.get(), body);
            saveMap(varAId, out, sku, "variation", parentAId);
            return;
        }

        JsonNode out = siteB.createVariation(parentBId, body);
        saveMap(varAId, out, sku, "variation", parentAId);
    }

    private Optional<Long> findVariationIdOnBBySku(long parentBId, String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        ArrayNode vars = siteB.listVariations(parentBId);
        for (JsonNode v : vars) {
            if (sku.equals(text(v.get("sku")))) {
                return Optional.of(v.get("id").asLong());
            }
        }
        return Optional.empty();
    }

    private void saveMap(long siteAId, JsonNode response, String sku, String type, Long siteAParentId) {
        long siteBId = response.get("id").asLong();
        ProductMap m = productMapRepository.findById(siteAId).orElseGet(ProductMap::new);
        m.setSiteAProductId(siteAId);
        m.setSiteBProductId(siteBId);
        m.setSku(sku);
        m.setProductType(type == null ? "simple" : type);
        m.setSiteAParentId(siteAParentId);
        productMapRepository.save(m);
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        String s = n.asText();
        return s.isBlank() ? null : s;
    }
}
