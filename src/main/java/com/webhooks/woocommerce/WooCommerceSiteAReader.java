package com.webhooks.woocommerce;

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
import com.webhooks.config.WooCommerceProperties;

/**
 * Read-only REST client for Site A (full product / variation payloads).
 */
@Component
public class WooCommerceSiteAReader {

    private final RestTemplate restTemplate;
    private final WooCommerceProperties props;
    private final ObjectMapper objectMapper;

    public WooCommerceSiteAReader(
        RestTemplate restTemplate,
        WooCommerceProperties props,
        ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public JsonNode getProduct(long productId) {
        requireSource();
        String url = UriComponentsBuilder
            .fromHttpUrl(trim(props.getSourceBaseUrl()))
            .path("/wp-json/wc/v3/products/" + productId)
            .queryParams(authParams())
            .encode()
            .toUriString();
        return getJson(url);
    }

    public JsonNode getVariation(long parentId, long variationId) {
        requireSource();
        String url = UriComponentsBuilder
            .fromHttpUrl(trim(props.getSourceBaseUrl()))
            .path("/wp-json/wc/v3/products/{parentId}/variations/{variationId}")
            .queryParams(authParams())
            .buildAndExpand(parentId, variationId)
            .encode()
            .toUriString();
        return getJson(url);
    }

    private JsonNode getJson(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            ResponseEntity<String> r = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            if (!r.getStatusCode().is2xxSuccessful() || r.getBody() == null) {
                throw new WooCommerceSyncException(r.getStatusCode().value(), "Unexpected response from Site A");
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

    private void requireSource() {
        if (!props.isSourceReadEnabled() || !props.hasSourceCredentials()) {
            throw new IllegalStateException("Site A REST credentials required for full product fetch — set woocommerce.source-*");
        }
    }

    private static String trim(String u) {
        if (u == null) {
            return "";
        }
        if (u.endsWith("/")) {
            return u.substring(0, u.length() - 1);
        }
        return u;
    }

    private MultiValueMap<String, String> authParams() {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        m.add("consumer_key", props.getSourceConsumerKey());
        m.add("consumer_secret", props.getSourceConsumerSecret());
        return m;
    }
}
