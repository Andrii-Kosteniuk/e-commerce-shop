package com.ecommerce.user.repository;

import com.ecommerce.user.model.Role;
import com.ecommerce.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @TestConfiguration
    static class TestCacheConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setFirstName("Andrii");
        testUser.setLastName("Kosteniuk");
        testUser.setEmail("andrii-kostenuk@gmail.com");
        testUser.setPassword("12345");
        testUser.setRole(Role.USER);

        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(testUser);
    }

    @Test
    void shouldFindSavedUserByEmail() {
        // ARRANGE
        User savedUser = userRepository.findByEmail(testUser.getEmail()).orElse(null);

        // ACT
        // ASSERT
        assertNotNull(savedUser);
        assertEquals(testUser.getUsername(), savedUser.getUsername());
        assertEquals(testUser.getPassword(), savedUser.getPassword());
    }

}


