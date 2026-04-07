package com.huah.ai.platform.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class RequestOriginResolver {

    private static final List<String> IP_HEADERS = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_REAL_IP");
    private static final List<String> COUNTRY_HEADERS = List.of("X-Country", "CF-IPCountry");
    private static final List<String> PROVINCE_HEADERS = List.of("X-Province", "X-Region");
    private static final List<String> CITY_HEADERS = List.of("X-City");
    private static final String LOCAL_COUNTRY = "本地网络";
    private static final String LOCAL_PROVINCE = "内网";
    private static final String LOCAL_CITY = "本机";

    private final RestTemplate restTemplate;
    private final String geoBaseUrl;
    private final boolean geoLookupEnabled;

    public RequestOriginResolver(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.request-origin.geo-base-url:https://ipwho.is}") String geoBaseUrl,
            @Value("${app.request-origin.geo-lookup-enabled:true}") boolean geoLookupEnabled,
            @Value("${app.request-origin.timeout-ms:2000}") long timeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(timeoutMs))
                .setReadTimeout(java.time.Duration.ofMillis(timeoutMs))
                .build();
        this.geoBaseUrl = trimTrailingSlash(geoBaseUrl);
        this.geoLookupEnabled = geoLookupEnabled;
    }

    public RequestOrigin resolve(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String country = firstNonBlank(readHeaderOrAttribute(request, COUNTRY_HEADERS));
        String province = firstNonBlank(readHeaderOrAttribute(request, PROVINCE_HEADERS));
        String city = firstNonBlank(readHeaderOrAttribute(request, CITY_HEADERS));

        if (StringUtils.hasText(country) || StringUtils.hasText(province) || StringUtils.hasText(city)) {
            return RequestOrigin.builder()
                    .clientIp(clientIp)
                    .country(country)
                    .province(province)
                    .city(city)
                    .build();
        }

        if (!StringUtils.hasText(clientIp)) {
            return RequestOrigin.builder().build();
        }
        if (isLocalIp(clientIp)) {
            return RequestOrigin.builder()
                    .clientIp(clientIp)
                    .country(LOCAL_COUNTRY)
                    .province(LOCAL_PROVINCE)
                    .city(LOCAL_CITY)
                    .build();
        }
        if (!geoLookupEnabled) {
            return RequestOrigin.builder().clientIp(clientIp).build();
        }
        return lookupByIp(clientIp);
    }

    private RequestOrigin lookupByIp(String clientIp) {
        try {
            URI uri = URI.create(geoBaseUrl + "/" + clientIp + "?lang=zh-CN");
            ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) {
                return RequestOrigin.builder().clientIp(clientIp).build();
            }
            Object success = body.get("success");
            if (Boolean.FALSE.equals(success)) {
                return RequestOrigin.builder().clientIp(clientIp).build();
            }
            return RequestOrigin.builder()
                    .clientIp(clientIp)
                    .country(asText(body.get("country")))
                    .province(asText(body.get("region")))
                    .city(asText(body.get("city")))
                    .build();
        } catch (RestClientException | IllegalArgumentException ex) {
            log.debug("request origin geo lookup failed: ip={}, error={}", clientIp, ex.getMessage());
            return RequestOrigin.builder().clientIp(clientIp).build();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            String resolved = firstForwardedIp(value);
            if (StringUtils.hasText(resolved) && !"unknown".equalsIgnoreCase(resolved)) {
                return normalizeIp(resolved);
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private String[] readHeaderOrAttribute(HttpServletRequest request, List<String> names) {
        return names.stream()
                .map(name -> {
                    String header = request.getHeader(name);
                    if (StringUtils.hasText(header)) {
                        return header;
                    }
                    Object attr = request.getAttribute(name);
                    return attr == null ? null : String.valueOf(attr);
                })
                .toArray(String[]::new);
    }

    private String firstForwardedIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item) && !"unknown".equalsIgnoreCase(item.trim())) {
                return item.trim();
            }
        }
        return null;
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("[")) {
            int end = normalized.indexOf(']');
            return end > 0 ? normalized.substring(1, end) : normalized;
        }
        int lastColon = normalized.lastIndexOf(':');
        if (lastColon > 0
                && normalized.indexOf(':') == lastColon
                && normalized.indexOf('.') > -1) {
            return normalized.substring(0, lastColon);
        }
        if ("0:0:0:0:0:0:0:1".equals(normalized)) {
            return "127.0.0.1";
        }
        return normalized;
    }

    private boolean isLocalIp(String ip) {
        if (ip.startsWith("127.")
                || "localhost".equalsIgnoreCase(ip)
                || "127.0.0.1".equals(ip)
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("::1")) {
            return true;
        }
        if (!ip.startsWith("172.")) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://ipwho.is";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
