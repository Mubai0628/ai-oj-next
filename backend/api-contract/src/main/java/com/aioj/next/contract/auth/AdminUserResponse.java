package com.aioj.next.contract.auth;

import com.aioj.next.common.security.Role;

import java.time.Instant;
import java.util.Set;

public record AdminUserResponse(
        Long userId,
        String account,
        String displayName,
        String email,
        boolean enabled,
        Set<Role> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
