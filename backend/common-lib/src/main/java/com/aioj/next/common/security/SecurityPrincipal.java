package com.aioj.next.common.security;

import java.util.Set;

public record SecurityPrincipal(Long userId, String account, Set<Role> roles) {
    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }
}

