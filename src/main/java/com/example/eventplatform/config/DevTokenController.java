package com.example.eventplatform.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Emissor de tokens para desenvolvimento e testes de carga. Registrado APENAS com o perfil
 * {@code local}; nunca é carregado em outros ambientes.
 */
@RestController
@Profile("local")
public class DevTokenController {

    private final NimbusJwtEncoder encoder;

    public DevTokenController(PlatformProperties props) {
        var key = new SecretKeySpec(props.security().devHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @PostMapping("/dev/token")
    public Map<String, String> issue(@RequestParam(defaultValue = "tenant-demo") String tenant,
                                     @RequestParam(defaultValue = "events:write events:read") String scope) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("event-platform-dev")
                .subject("dev-user")
                .audience(java.util.List.of("event-platform"))
                .issuedAt(now)
                .expiresAt(now.plus(12, ChronoUnit.HOURS))
                .claim("tenant_id", tenant)
                .claim("scope", scope)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer");
    }
}
