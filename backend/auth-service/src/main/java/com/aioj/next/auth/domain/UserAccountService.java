package com.aioj.next.auth.domain;

import com.aioj.next.auth.entity.RefreshTokenEntity;
import com.aioj.next.auth.entity.UserEntity;
import com.aioj.next.auth.entity.UserRoleEntity;
import com.aioj.next.auth.mapper.RefreshTokenMapper;
import com.aioj.next.auth.mapper.UserMapper;
import com.aioj.next.auth.mapper.UserRoleMapper;
import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.common.security.Role;
import com.aioj.next.contract.auth.AdminUserCreateRequest;
import com.aioj.next.contract.auth.AdminUserResponse;
import com.aioj.next.contract.auth.AdminUserUpdateRequest;
import com.aioj.next.contract.auth.PasswordUpdateRequest;
import com.aioj.next.contract.auth.UserProfileResponse;
import com.aioj.next.contract.auth.UserUpdateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAccountService {
    private static final Set<Role> ALLOWED_ROLES = Set.of(Role.STUDENT, Role.TEACHER, Role.ADMIN);

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RefreshTokenMapper refreshTokenMapper;

    public UserAccountService(PasswordEncoder passwordEncoder, UserMapper userMapper, UserRoleMapper userRoleMapper,
                              RefreshTokenMapper refreshTokenMapper) {
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
    }

    @PostConstruct
    void seedUsers() {
        createSeedIfAbsent("admin", "Admin@123456", "Platform Admin", "admin@example.edu",
                Set.of(Role.ADMIN, Role.TEACHER));
        createSeedIfAbsent("teacher", "Teacher@123456", "Demo Teacher", "teacher@example.edu", Set.of(Role.TEACHER));
        createSeedIfAbsent("student", "Student@123456", "Demo Student", "student@example.edu", Set.of(Role.STUDENT));
    }

    @Transactional
    public UserAccount register(String account, String rawPassword, String displayName, String email) {
        if (findUserByAccount(account) != null) {
            throw new DomainException(ErrorCode.CONFLICT, "Account already exists");
        }
        UserEntity user = newUser(account, rawPassword, displayName, email, true);
        userMapper.insert(user);
        replaceRoles(user.getId(), Set.of(Role.STUDENT));
        return toAccount(user, Set.of(Role.STUDENT));
    }

    public UserAccount login(String account, String rawPassword) {
        UserEntity user = findUserByAccount(account);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Invalid account or password");
        }
        return toAccount(user, rolesForUser(user.getId()));
    }

    public UserAccount getById(Long userId) {
        UserEntity user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        return toAccount(user, rolesForUser(userId));
    }

    public UserProfileResponse getProfile(Long userId) {
        UserAccount user = getById(userId);
        return new UserProfileResponse(user.id(), user.account(), user.displayName(), user.email(), user.roles());
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        UserEntity user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        user.setDisplayName(request.displayName());
        user.setEmail(normalizeBlank(request.email()));
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        return getProfile(userId);
    }

    @Transactional
    public void updatePassword(Long userId, PasswordUpdateRequest request) {
        UserEntity user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "User is disabled");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        revokeUserRefreshTokens(userId);
    }

    @Transactional
    public void storeRefreshToken(Long userId, String refreshToken, Instant expiresAt) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(sha256(refreshToken));
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(Instant.now());
        refreshTokenMapper.insert(entity);
    }

    @Transactional
    public UserAccount refresh(String refreshToken, Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        RefreshTokenEntity token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, sha256(refreshToken)));
        if (token == null || !Objects.equals(token.getUserId(), userId)
                || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new DomainException(ErrorCode.UNAUTHORIZED, "Refresh token is invalid");
        }
        token.setRevokedAt(Instant.now());
        refreshTokenMapper.updateById(token);
        return getById(userId);
    }

    @Transactional
    public boolean revokeRefreshToken(String refreshToken) {
        RefreshTokenEntity token = refreshTokenMapper.selectOne(new LambdaQueryWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getTokenHash, sha256(refreshToken)));
        if (token == null || token.getRevokedAt() != null) {
            return false;
        }
        token.setRevokedAt(Instant.now());
        refreshTokenMapper.updateById(token);
        return true;
    }

    public PageResponse<AdminUserResponse> listUsers(long page, long pageSize, String search, Role role, Boolean enabled) {
        long safePage = Math.max(page, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Set<Long> roleUserIds = null;
        if (role != null) {
            roleUserIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                            .eq(UserRoleEntity::getRole, role))
                    .stream()
                    .map(UserRoleEntity::getUserId)
                    .collect(Collectors.toSet());
            if (roleUserIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0, safePage, safePageSize);
            }
        }

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(enabled != null, UserEntity::getEnabled, enabled)
                .in(roleUserIds != null, UserEntity::getId, roleUserIds)
                .and(hasText(search), query -> query
                        .like(UserEntity::getAccount, search)
                        .or()
                        .like(UserEntity::getDisplayName, search)
                        .or()
                        .like(UserEntity::getEmail, search))
                .orderByDesc(UserEntity::getCreatedAt);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        Map<Long, Set<Role>> roles = rolesForUsers(result.getRecords().stream().map(UserEntity::getId).toList());
        List<AdminUserResponse> records = result.getRecords().stream()
                .map(user -> toAdminResponse(user, roles.getOrDefault(user.getId(), Set.of())))
                .toList();
        return new PageResponse<>(records, result.getTotal(), safePage, safePageSize);
    }

    public AdminUserResponse getAdminUser(Long userId) {
        UserEntity user = requireUser(userId);
        return toAdminResponse(user, rolesForUser(userId));
    }

    @Transactional
    public AdminUserResponse createAdminUser(AdminUserCreateRequest request) {
        if (findUserByAccount(request.account()) != null) {
            throw new DomainException(ErrorCode.CONFLICT, "Account already exists");
        }
        Set<Role> roles = requireRoles(request.roles());
        UserEntity user = newUser(request.account(), request.password(), request.displayName(), request.email(),
                request.enabled() == null || request.enabled());
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
        return toAdminResponse(user, roles);
    }

    @Transactional
    public AdminUserResponse updateAdminUser(Long userId, AdminUserUpdateRequest request) {
        UserEntity user = requireUser(userId);
        Set<Role> roles = requireRoles(request.roles());
        user.setDisplayName(request.displayName());
        user.setEmail(normalizeBlank(request.email()));
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);
        replaceRoles(userId, roles);
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            revokeUserRefreshTokens(userId);
        }
        return toAdminResponse(user, roles);
    }

    @Transactional
    public void disableUser(Long userId) {
        UserEntity user = requireUser(userId);
        if (Boolean.TRUE.equals(user.getEnabled())) {
            user.setEnabled(false);
            user.setUpdatedAt(Instant.now());
            userMapper.updateById(user);
        }
        revokeUserRefreshTokens(userId);
    }

    private void createSeedIfAbsent(String account, String password, String displayName, String email, Set<Role> roles) {
        if (findUserByAccount(account) != null) {
            return;
        }
        UserEntity user = newUser(account, password, displayName, email, true);
        userMapper.insert(user);
        replaceRoles(user.getId(), roles);
    }

    private UserEntity newUser(String account, String rawPassword, String displayName, String email, boolean enabled) {
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setAccount(account);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setEmail(normalizeBlank(email));
        user.setEnabled(enabled);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "User not found");
        }
        return user;
    }

    private UserEntity findUserByAccount(String account) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getAccount, account));
    }

    private Set<Role> rolesForUser(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId))
                .stream()
                .map(UserRoleEntity::getRole)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    private Map<Long, Set<Role>> rolesForUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .in(UserRoleEntity::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(UserRoleEntity::getUserId,
                        Collectors.mapping(UserRoleEntity::getRole,
                                Collectors.toCollection(() -> EnumSet.noneOf(Role.class)))));
    }

    private void replaceRoles(Long userId, Set<Role> roles) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
        roles.forEach(role -> userRoleMapper.insert(new UserRoleEntity(userId, role)));
    }

    private Set<Role> requireRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty() || !ALLOWED_ROLES.containsAll(roles)) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Invalid roles");
        }
        return roles.stream().collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
    }

    private UserAccount toAccount(UserEntity user, Set<Role> roles) {
        return new UserAccount(user.getId(), user.getAccount(), user.getPasswordHash(), user.getDisplayName(),
                user.getEmail(), Boolean.TRUE.equals(user.getEnabled()), Set.copyOf(roles));
    }

    private AdminUserResponse toAdminResponse(UserEntity user, Set<Role> roles) {
        return new AdminUserResponse(user.getId(), user.getAccount(), user.getDisplayName(), user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled()), Set.copyOf(roles), user.getCreatedAt(), user.getUpdatedAt());
    }

    @Transactional
    public void revokeUserRefreshTokens(Long userId) {
        refreshTokenMapper.update(null, new LambdaUpdateWrapper<RefreshTokenEntity>()
                .eq(RefreshTokenEntity::getUserId, userId)
                .isNull(RefreshTokenEntity::getRevokedAt)
                .set(RefreshTokenEntity::getRevokedAt, Instant.now()));
    }

    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash refresh token", ex);
        }
    }

    private String normalizeBlank(String value) {
        return hasText(value) ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
