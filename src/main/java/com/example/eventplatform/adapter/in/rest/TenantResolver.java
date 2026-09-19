package com.example.eventplatform.adapter.in.rest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * O tenant vem sempre do token assinado, nunca do corpo da requisição: um cliente não
 * consegue gravar ou ler dados de outro tenant alterando o payload.
 */
final class TenantResolver {

    static final String TENANT_CLAIM = "tenant_id";

    private TenantResolver() {
    }

    static String tenantOf(Jwt jwt) {
        String tenant = jwt == null ? null : jwt.getClaimAsString(TENANT_CLAIM);
        if (tenant == null || tenant.isBlank()) {
            throw new AccessDeniedException("token sem claim " + TENANT_CLAIM);
        }
        return tenant;
    }
}
