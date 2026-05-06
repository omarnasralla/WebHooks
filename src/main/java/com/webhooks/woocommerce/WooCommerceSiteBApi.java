package com.webhooks.woocommerce;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.webhooks.config.WooCommerceProperties;

/**
 * WooCommerce REST API on Site B (create/update/delete).
 */
@Component
public class WooCommerceSiteBApi {

    private final RestTemplate restTemplate;
    private final WooCommerceProperties props;
    private final ObjectMapper objectMapper;

    public WooCommerceSiteBApi(
        RestTemplate restTemplate,
        WooCommerceProperties props,
        ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public void validateConfigured() {
        if (props.getTargetBaseUrl() == null || props.getTargetBaseUrl().isBlank()) {
            throw new IllegalStateException("woocommerce.target-base-url is not set");
        }
        if (props.getTargetConsumerKey() == null || props.getTargetConsumerSecret() == null
            || props.getTargetConsumerKey().isBlank() || props.getTargetConsumerSecret().isBlank()) {
            throw new IllegalStateException("woocommerce.target-consumer-key / target-consumer-secret must be set");
        }
    }

    public Optional<JsonNode> findProductBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        String url = UriComponentsBuilder
            .fromHttpUrl(base())
            .path("/wp-json/wc/v3/products")
            .queryParam("sku", sku)
            .queryParams(authParams())
            .encode()
            .toUriString();
        ArrayNode arr = getArray(url);
        if (arr.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(arr.get(0));
    }

    /** All variations for a parent product on B (used to resolve variation id by SKU). */
    public ArrayNode listVariations(long parentProductIdOnB) {
        String url = UriComponentsBuilder
            .fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + parentProductIdOnB + "/variations")
            .queryParam("per_page", "100")
            .queryParams(authParams())
            .encode()
            .toUriString();
        return getArray(url);
    }

    public JsonNode createProduct(JsonNode body) {
        return postJson(UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products")
            .queryParams(authParams())
            .encode().toUriString(), body);
    }

    public JsonNode updateProduct(long id, JsonNode body) {
        String url = UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + id)
            .queryParams(authParams())
            .encode()
            .toUriString();
        return putJson(url, body);
    }

    public void deleteProduct(long id) {
        String url = UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + id)
            .queryParam("force", "true")
            .queryParams(authParams())
            .encode()
            .toUriString();
        exchangeVoid(url, HttpMethod.DELETE);
    }

    public JsonNode createVariation(long parentId, JsonNode body) {
        String url = UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + parentId + "/variations")
            .queryParams(authParams())
            .encode()
            .toUriString();
        return postJson(url, body);
    }

    public JsonNode updateVariation(long parentId, long variationId, JsonNode body) {
        String url = UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + parentId + "/variations/" + variationId)
            .queryParams(authParams())
            .encode()
            .toUriString();
        return putJson(url, body);
    }

    public void deleteVariation(long parentId, long variationId) {
        String url = UriComponentsBuilder.fromHttpUrl(base())
            .path("/wp-json/wc/v3/products/" + parentId + "/variations/" + variationId)
            .queryParam("force", "true")
            .queryParams(authParams())
            .encode()
            .toUriString();
        exchangeVoid(url, HttpMethod.DELETE);
    }

    private ArrayNode getArray(String url) {
        JsonNode n = getJson(url);
        if (n instanceof ArrayNode an) {
            return an;
        }
        throw new WooCommerceSyncException(500, "Expected JSON array from " + url);
    }

    private JsonNode getJson(String url) {
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, jsonHeadersEntity(), String.class
            );
            if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
                throw new WooCommerceSyncException(r.getStatusCode().value(), "GET failed: " + url);
            }
            return objectMapper.readTree(r.getBody());
        } catch (HttpStatusCodeException e) {
            throw new WooCommerceSyncException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (WooCommerceSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new WooCommerceSyncException(0, e.getMessage(), e);
        }
    }

    private JsonNode postJson(String url, JsonNode body) {
        return writeJson(HttpMethod.POST, url, body);
    }

    private JsonNode putJson(String url, JsonNode body) {
        return writeJson(HttpMethod.PUT, url, body);
    }

    private JsonNode writeJson(HttpMethod method, String url, JsonNode body) {
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), h);
            ResponseEntity<String> r = restTemplate.exchange(URI.create(url), method, entity, String.class);
            if (r.getBody() == null || r.getBody().isBlank()) {
                throw new WooCommerceSyncException(r.getStatusCode().value(), "Empty body from " + url);
            }
            return objectMapper.readTree(r.getBody());
        } catch (HttpStatusCodeException e) {
            throw new WooCommerceSyncException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        } catch (WooCommerceSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new WooCommerceSyncException(0, e.getMessage(), e);
        }
    }

    private void exchangeVoid(String url, HttpMethod method) {
        try {
            ResponseEntity<Void> r = restTemplate.exchange(URI.create(url), method, jsonHeadersEntity(), Void.class);
            if (!r.getStatusCode().is2xxSuccessful()) {
                throw new WooCommerceSyncException(r.getStatusCode().value(), method + " " + url);
            }
        } catch (HttpStatusCodeException e) {
            throw new WooCommerceSyncException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private HttpEntity<Void> jsonHeadersEntity() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(h);
    }

    private String base() {
        String u = trim(props.getTargetBaseUrl());
        if (u.isEmpty()) {
            throw new IllegalStateException("woocommerce.target-base-url is not set");
        }
        return u;
    }

    private static String trim(String u) {
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }

    private MultiValueMap<String, String> authParams() {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        m.add("consumer_key", props.getTargetConsumerKey());
        m.add("consumer_secret", props.getTargetConsumerSecret());
        return m;
    }
}
