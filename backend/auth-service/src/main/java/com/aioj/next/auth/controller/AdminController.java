package com.aioj.next.auth.controller;

import com.aioj.next.auth.domain.UserAccountService;
import com.aioj.next.common.api.ApiResponse;
import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.security.Role;
import com.aioj.next.contract.auth.AdminUserCreateRequest;
import com.aioj.next.contract.auth.AdminUserResponse;
import com.aioj.next.contract.auth.AdminUserUpdateRequest;
import com.aioj.next.contract.auth.RoleResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserAccountService userAccountService;

    public AdminController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(Arrays.stream(Role.values())
                .map(role -> new RoleResponse(role, label(role)))
                .toList());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(userAccountService.listUsers(page, pageSize, search, role, enabled));
    }

    @PostMapping("/users")
    public ApiResponse<AdminUserResponse> createUser(@RequestBody @Valid AdminUserCreateRequest request) {
        return ApiResponse.ok(userAccountService.createAdminUser(request));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(userAccountService.getAdminUser(id));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable Long id,
                                                     @RequestBody @Valid AdminUserUpdateRequest request) {
        return ApiResponse.ok(userAccountService.updateAdminUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Boolean> deleteUser(@PathVariable Long id) {
        userAccountService.disableUser(id);
        return ApiResponse.ok(Boolean.TRUE);
    }

    private String label(Role role) {
        return switch (role) {
            case STUDENT -> "Student";
            case TEACHER -> "Teacher";
            case ADMIN -> "Admin";
        };
    }
}
