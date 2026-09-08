package com.ecommerce.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class TokenBlocklistServiceTest {

    private static final String PREFIX = "blocklist";

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @InjectMocks
    TokenBlocklistService tokenBlocklistService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                tokenBlocklistService,
                "blocklistPrefix",
                PREFIX
        );
    }

    @Test
    void shouldReadTokenFromRedisAndDefineWhetherTokenIsRevoked() {
        // ARRANGE
        when(redisTemplate.hasKey(PREFIX + ":token123")).thenReturn(true);

        // ACT
        boolean revoked = tokenBlocklistService.isRevoked("token123");

        // ASSERT
        assertTrue(revoked);
        verify(redisTemplate).hasKey(PREFIX + ":token123");
    }

    @Test
    void shouldRevokeTokenDirectly() {
        // ARRANGE
        String tokenId = "token123";
        long expiry = 5000L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // ACT
        tokenBlocklistService.revoke(tokenId, expiry);

        // ASSERT
        verify(valueOperations).set(
                PREFIX + ":token123",
                "revoked",
                expiry,
                TimeUnit.MILLISECONDS
        );
    }


}
