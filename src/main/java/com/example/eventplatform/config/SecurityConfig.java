package com.example.eventplatform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * API stateless protegida por OAuth2/JWT (resource server).
 * <ul>
 *   <li>Escrita exige o escopo {@code events:write}; leitura exige {@code events:read}.</li>
 *   <li>Em produção, o emissor é um IdP (Keycloak, Auth0, Cognito...) via {@code issuer-uri} — chaves
 *       públicas rotacionáveis via JWKS.</li>
 *   <li>Endpoints do Actuator ficam em porta de gerenciamento separada (8081), não exposta pelo Ingress.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // sem cookies de sessão: CSRF não se aplica
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/dev/token").permitAll() // só existe com o perfil "local"
                        .requestMatchers(HttpMethod.POST, "/api/v1/events/**").hasAuthority("SCOPE_events:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/**").hasAuthority("SCOPE_events:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /**
     * Somente para desenvolvimento/testes de carga locais: valida tokens HS256 com segredo
     * compartilhado. Em produção esta propriedade não é definida e o Spring Boot configura o
     * decoder a partir de {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
     */
    @Bean
    @ConditionalOnExpression("'${platform.security.dev-hmac-secret:}' != ''")
    JwtDecoder devJwtDecoder(PlatformProperties props) {
        byte[] secret = props.security().devHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("platform.security.dev-hmac-secret deve ter ao menos 32 bytes");
        }
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
