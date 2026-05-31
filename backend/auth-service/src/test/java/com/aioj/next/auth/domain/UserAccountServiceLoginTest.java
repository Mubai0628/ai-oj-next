package com.aioj.next.auth.domain;

import com.aioj.next.auth.entity.UserEntity;
import com.aioj.next.auth.mapper.RefreshTokenMapper;
import com.aioj.next.auth.mapper.UserMapper;
import com.aioj.next.auth.mapper.UserRoleMapper;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceLoginTest {
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(passwordEncoder, userMapper, userRoleMapper, refreshTokenMapper);
    }

    @Test
    void loginWithDisabledAccountAndCorrectPasswordReturnsDisabledAccountMessage() {
        UserEntity user = user(false);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        DomainException error = assertThrows(DomainException.class, () -> service.login("student", "secret"));

        assertEquals(ErrorCode.FORBIDDEN, error.errorCode());
        assertEquals("Account is disabled. Please contact an administrator.", error.getMessage());
    }

    @Test
    void loginWithDisabledAccountAndWrongPasswordStillReturnsGenericCredentialFailure() {
        UserEntity user = user(false);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        DomainException error = assertThrows(DomainException.class, () -> service.login("student", "wrong"));

        assertEquals(ErrorCode.UNAUTHORIZED, error.errorCode());
        assertEquals("Invalid account or password", error.getMessage());
    }

    private UserEntity user(boolean enabled) {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setAccount("student");
        user.setPasswordHash("hash");
        user.setDisplayName("Demo Student");
        user.setEnabled(enabled);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
