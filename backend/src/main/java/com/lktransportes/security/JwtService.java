package com.lktransportes.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class JwtService {


    /**
     * Em produção vem de JWT_SECRET e o boot falha se não vier — ver
     * application-prod.yml. O padrão abaixo só serve para desenvolvimento.
     */
    @Value("${jwt.secret:lk-transportes-dev-secret-troque-em-producao-0000000000000000}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String gerarToken(UUID usuarioId, String email, String papel) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("email", email)
                .claim("papel", papel)
                .issuedAt(java.util.Date.from(agora))
                .expiration(java.util.Date.from(agora.plus(7, ChronoUnit.DAYS)))
                .signWith(key())
                .compact();
    }

    public String extrairUsuarioId(String token) {
        return Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    /** Devolve id e papel de um token válido, ou vazio se estiver expirado/adulterado. */
    public java.util.Optional<Identidade> ler(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return java.util.Optional.of(new Identidade(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("papel", String.class)));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    public record Identidade(UUID id, String email, String papel) {}
}
