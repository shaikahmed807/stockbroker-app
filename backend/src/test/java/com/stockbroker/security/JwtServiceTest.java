package com.stockbroker.security;

import com.stockbroker.TestData;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService – token issue and validation")
class JwtServiceTest {

    private static final String OTHER_SECRET =
            "YW5vdGhlci1jb21wbGV0ZWx5LWRpZmZlcmVudC10ZXN0LXNpZ25pbmcta2V5LTA5ODc2NTQzMjE=";

    private final JwtService jwt = new JwtService(TestData.SECRET, 60_000);

    @Test
    void generatedTokenCarriesSubjectRoleAndId() {
        String token = jwt.generateToken("jane@example.com", "USER");

        Optional<Claims> claims = jwt.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("jane@example.com");
        assertThat(claims.get().get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get().getId()).isNotBlank();
    }

    @Test
    void expiryMatchesConfiguredLifetime() {
        long before = System.currentTimeMillis();
        Claims claims = jwt.parse(jwt.generateToken("a@b.com", "USER")).orElseThrow();

        long expiresIn = claims.getExpiration().getTime() - before;
        assertThat(expiresIn).isBetween(55_000L, 61_000L);
        assertThat(jwt.getExpirationMs()).isEqualTo(60_000L);
    }

    @Test
    void everyTokenGetsAUniqueIdForLogoutBlacklisting() {
        String id1 = jwt.parse(jwt.generateToken("a@b.com", "USER")).orElseThrow().getId();
        String id2 = jwt.parse(jwt.generateToken("a@b.com", "USER")).orElseThrow().getId();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService attacker = new JwtService(OTHER_SECRET, 60_000);
        String forged = attacker.generateToken("admin@example.com", "ADMIN");
        assertThat(jwt.parse(forged)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.generateToken("jane@example.com", "USER");
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(TestData.SECRET, -1_000);
        String expired = shortLived.generateToken("jane@example.com", "USER");
        assertThat(jwt.parse(expired)).isEmpty();
    }
}
