package com.ecommerce.user.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private final String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationTime", 15 * 60 * 1000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpirationTime", 7 * 24 * 60 * 60 * 1000L);
    }


    @Test
    void shouldGenerateAccessToken() {

        // GIVEN
        String token = jwtService.generateAccessToken(10L, "andrii-kosteniuk@gmail.com", "USER");

        // WHEN
        Claims claims = jwtService.extractClaims(token);

        // THEN
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(claims)
                .containsEntry("userId", 10)
                .containsEntry("sub", "andrii-kosteniuk@gmail.com")
                .containsEntry("role", "USER")
                .containsEntry("typ", "access");

        assertThat(claims.get("tokenId", String.class)).isNotBlank();

    }


    @Test
    void shouldReturnFalseWhenValidateTokenIfTokenIsExpired() {
        // GIVEN
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationTime", -1000L);

        // WHEN
        String expired = jwtService.generateAccessToken(1L, "andrii-kosteniuk@gmail.com", "USER");

        // THEN
        assertThat(jwtService.validateToken(expired)).isFalse();
    }

    @Test
    void shouldRetrieveRolesFromToken() {

        // GIVEN
        String token = jwtService.generateAccessToken(10L, "andrii-kosteniuk@gmail.com", "USER");

        // WHEN
        String actualRole = jwtService.getUserRoleFromToken(token);

        // THEN
        assertEquals("USER", actualRole);
    }

    @Test
    void shouldGenerateRefreshToken() {

        // GIVEN
        String token = jwtService.generateRefreshToken(10L, "andrii-kosteniuk@gmail.com", "USER");

        // WHEN
        Claims claims = jwtService.extractClaims(token);

        // THEN
        assertThat(claims)
                .containsEntry("userId", 10)
                .containsEntry("sub", "andrii-kosteniuk@gmail.com")
                .containsEntry("role", "USER")
                .containsEntry("typ", "refresh");

        assertThat(claims.get("tokenId", String.class)).isNotBlank();

    }
}
