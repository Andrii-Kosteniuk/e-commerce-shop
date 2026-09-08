package com.ecommerce.user.service;

import com.ecommerce.commondto.auth.*;
import com.ecommerce.commondto.user.UserResponse;
import com.ecommerce.commonexception.exception.ResourceAlreadyExistsException;
import com.ecommerce.user.jwt.JwtService;
import com.ecommerce.user.mapper.UserMapper;
import com.ecommerce.user.model.Role;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.impl.UserAuthenticationServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UserAuthServiceTests {

    @Mock
    UserRepository userRepository;
    @Mock
    UserRepositoryService userRepositoryService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    TokenBlocklistService tokenBlocklistService;
    @Mock
    JwtService jwtService;
    @Mock
    UserMapper userMapper;

    private UserAuthenticationServiceImpl userAuthenticationService() {
        return new UserAuthenticationServiceImpl(
                userRepository, userRepositoryService, passwordEncoder,
                authenticationManager, tokenBlocklistService, jwtService, userMapper);
    }
    

    @Test
    void shouldRegisterUserAndReturnTokenPair() {

        // ARRANGE
        var request = new RegisterRequest("Andrii", "Kosteniuk", "andrii@gmail.com", "pass1234", "USER");
        var savedUser = new User(1L, "Andrii", "Kosteniuk", "andrii@gmail.com", "encoded-password", Role.USER);
        var userResponse = new UserResponse(1L, "Andrii", "Kosteniuk", "andrii@gmail.com", "USER");
        

        when(userRepository.existsByEmail("andrii@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("pass1234")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.userToUserResponse(savedUser)).thenReturn(userResponse);

        when(jwtService.generateAccessToken(1L, "andrii@gmail.com", "USER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(1L, "andrii@gmail.com", "USER")).thenReturn("refresh-token");

        // ACT
        var response = userAuthenticationService().registerUser(request);

        // ASSERT
        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldThrowResourceAlreadyExistsExceptionIfUserAlreadyExists() {

        // ARRANGE
        var request = new RegisterRequest("Andrii", "Kosteniuk", "andrii@gmail.com", "pass1234", "USER");

        when(userRepository.existsByEmail("andrii@gmail.com")).thenReturn(true);

        // ACT
        // ASSERT
        assertThrowsExactly(ResourceAlreadyExistsException.class, () -> userAuthenticationService().registerUser(request));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldAuthenticateUser() {

        // ARRANGE
        var request = new AuthenticationRequest("andrii@gmail.com", "pass1234");
        var userResponse = new UserResponse(1L, "Andrii", "Kosteniuk", "andrii@gmail.com", "USER");

        when(userRepositoryService.getUserByEmail("andrii@gmail.com")).thenReturn(userResponse);
        when(jwtService.generateAccessToken(1L, "andrii@gmail.com", "USER")).thenReturn("access-token");
        when(jwtService.generateRefreshToken(1L, "andrii@gmail.com", "USER")).thenReturn("refresh-token");

        // ACT
        AuthenticationResponse response = userAuthenticationService().authenticate(request);
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);

        // ASSERT

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        verify(authenticationManager).authenticate(captor.capture());
    }

    @Test
    void shouldFailFastWhenBadCredentials() {

        // ARRANGE
        var request = new AuthenticationRequest("andrii@gmail.com", "wrong-password");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        // ACT
        // ASSERT
        assertThrowsExactly(BadCredentialsException.class, () -> userAuthenticationService().authenticate(request));
        verifyNoInteractions(userRepositoryService, jwtService);
    }

    @Test
    void shouldRefreshTokenSuccessfully() {

        // ARRANGE
        var tokenRequest = new RefreshTokenRequest("some-refresh-jwt");
        Claims claims = mock(Claims.class);
        when(claims.get("typ", String.class)).thenReturn("refresh");
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(claims.get("role", String.class)).thenReturn("USER");
        when(claims.getSubject()).thenReturn("andrii@gmail.com");
        when(jwtService.extractClaims("some-refresh-jwt")).thenReturn(claims);
        when(jwtService.generateAccessToken(1L, "andrii@gmail.com", "USER")).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(1L, "andrii@gmail.com", "USER")).thenReturn("new-refresh-token");

        // ACT
        AuthenticationResponse response = userAuthenticationService().refreshToken(tokenRequest);

        // ASSERT
        assertThat(response.token()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    void shouldThrowJwtExceptionIfTokenHasInvalidTypeWhenRefresh() {

        // ARRANGE
        var tokenRequest = new RefreshTokenRequest("some-refresh-jwt");
        Claims claims = mock(Claims.class);
        when(claims.get("typ", String.class)).thenReturn("invalid-type");

        when(jwtService.extractClaims("some-refresh-jwt")).thenReturn(claims);
        when(claims.get("typ", String.class)).thenReturn("invalid-type");

        // ACT

        // ASSERT
        assertThrowsExactly(JwtException.class, () -> userAuthenticationService().refreshToken(tokenRequest));

        verify(jwtService).extractClaims("some-refresh-jwt");
        verify(jwtService, never()).generateAccessToken(anyLong(), anyString(), anyString());
        verify(jwtService, never()).generateRefreshToken(anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRevokeAccessAndRefreshTokensOnLogout() {

        // ARRANGE
        var request = new LogOutRequest("access", "refresh");

        Claims accessClaims = mock(Claims.class);
        when(accessClaims.get("typ", String.class)).thenReturn("access");
        when(accessClaims.get("tokenId", String.class)).thenReturn("access-id");
        when(accessClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.get("typ", String.class)).thenReturn("refresh");
        when(refreshClaims.get("tokenId", String.class)).thenReturn("refresh-id");
        when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 600_000));

        when(jwtService.extractClaims("access")).thenReturn(accessClaims);
        when(jwtService.extractClaims("refresh")).thenReturn(refreshClaims);

        // ACT
        userAuthenticationService().logout(request);

        // ASSERT
        verify(tokenBlocklistService).revoke(eq("access-id"), anyLong());
        verify(tokenBlocklistService).revoke(eq("refresh-id"), anyLong());
    }

    @Test
    void shouldSkipsRevocationForAlreadyExpiredToken() {

        // ARRANGE
        var request = new LogOutRequest("expired-access-jwt", "refresh-jwt");

        Claims expiredClaims = mock(Claims.class);
        when(expiredClaims.get("typ", String.class)).thenReturn("access");
        when(expiredClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 60_000));

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.get("typ", String.class)).thenReturn("refresh");
        when(refreshClaims.get("tokenId", String.class)).thenReturn("refresh-id");
        when(refreshClaims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        when(jwtService.extractClaims("expired-access-jwt")).thenReturn(expiredClaims);
        when(jwtService.extractClaims("refresh-jwt")).thenReturn(refreshClaims);

        // ACT
        userAuthenticationService().logout(request);

        // ASSERT
        verify(tokenBlocklistService, never()).revoke(eq("access-id"), anyLong());
        verify(tokenBlocklistService).revoke(eq("refresh-id"), anyLong());
    }

    @Test
    void shouldNotRevokeIfTokenHasInvalidType() {

        // ARRANGE
        var request = new LogOutRequest("access-jwt", "refresh-jwt");

        Claims wrongAccessClaims = mock(Claims.class);
        when(wrongAccessClaims.get("typ", String.class)).thenReturn("refresh-jwt");

        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.get("typ", String.class)).thenReturn("refresh-jwt");

        when(jwtService.extractClaims("access-jwt")).thenReturn(wrongAccessClaims);
        when(jwtService.extractClaims("refresh-jwt")).thenReturn(refreshClaims);

        // ACT
        // ASSERT
        assertDoesNotThrow(() -> userAuthenticationService().logout(request));

        verify(tokenBlocklistService, never()).revoke(eq("access-id"), anyLong());
        verify(tokenBlocklistService, never()).revoke(eq("refresh-id"), anyLong());
    }

}
