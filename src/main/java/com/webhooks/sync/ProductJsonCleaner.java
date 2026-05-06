package com.webhooks.sync;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Prepares WooCommerce product JSON so it can be applied on Site B without carrying Site A ids.
 */
@Component
public class ProductJsonCleaner {

    private static final Set<String> DROP_FIELDS = Set.of(
        "id",
        "parent_id",
        "_links",
        "permalink",
        "slug", // avoid URL collisions — Site B assigns or keeps existing
        "date_created",
        "date_created_gmt",
        "date_modified",
        "date_modified_gmt",
        "date_on_sale_from",
        "date_on_sale_to",
        "date_on_sale_from_gmt",
        "date_on_sale_to_gmt",
        "total_sales",
        "average_rating",
        "rating_count",
        "price",
        "on_sale",
        "purchasable",
        "menu_order",
        "post_password"
    );

    private final ObjectMapper objectMapper;

    public ProductJsonCleaner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param source product or variation JSON from Site A
     * @param parentIdOnB required for variation payloads (parent id on Site B)
     * @param variableParent when true, omit variation id list (parent create/update on B)
     */
    public ObjectNode forMirror(JsonNode source, Long parentIdOnB, boolean variableParent) {
        if (source == null || !source.isObject()) {
            throw new IllegalArgumentException("Product JSON must be a JSON object");
        }
        ObjectNode o = (ObjectNode) source.deepCopy();
        List<String> removeNames = new ArrayList<>();
        Iterator<String> it = o.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            if (DROP_FIELDS.contains(name)) {
                removeNames.add(name);
            }
        }
        removeNames.forEach(o::remove);
        if (variableParent) {
            o.remove("variations");
        }
        if (parentIdOnB != null) {
            o.put("parent_id", parentIdOnB);
        } else {
            o.remove("parent_id");
        }
        rewriteCategories(o);
        rewriteImages(o);
        filterInternalMeta(o);
        return o;
    }

    private void rewriteCategories(ObjectNode o) {
        if (!o.has("categories") || !o.get("categories").isArray()) {
            return;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode c : o.get("categories")) {
            if (c.hasNonNull("name")) {
                ObjectNode n = objectMapper.createObjectNode();
                n.set("name", c.get("name"));
                out.add(n);
            }
        }
        o.set("categories", out);
    }

    private void rewriteImages(ObjectNode o) {
        if (!o.has("images") || !o.get("images").isArray()) {
            return;
        }
        ArrayNode out = objectMapper.createArrayNode();
        for (JsonNode img : o.get("images")) {
            if (img.hasNonNull("src")) {
                ObjectNode n = objectMapper.createObjectNode();
                n.set("src", img.get("src"));
                if (img.has("name")) {
                    n.set("name", img.get("name"));
                }
                if (img.has("alt")) {
                    n.set("alt", img.get("alt"));
                }
                out.add(n);
            }
        }
        o.set("images", out);
    }

    private void filterInternalMeta(ObjectNode o) {
        if (!o.has("meta_data") || !o.get("meta_data").isArray()) {
            return;
        }
        ArrayNode meta = (ArrayNode) o.get("meta_data");
        ArrayNode kept = objectMapper.createArrayNode();
        for (JsonNode m : meta) {
            if (m.has("key")) {
                String key = m.get("key").asText("");
                if (!key.startsWith("_")) {
                    kept.add(m);
                }
            }
        }
        o.set("meta_data", kept);
    }
}
