package com.aioj.next.auth.controller;

import com.aioj.next.auth.domain.UserAccountService;
import com.aioj.next.common.api.ApiResponse;
import com.aioj.next.common.security.SecuritySupport;
import com.aioj.next.contract.auth.PasswordUpdateRequest;
import com.aioj.next.contract.auth.UserProfileResponse;
import com.aioj.next.contract.auth.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
public class UserController {
    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(userAccountService.getProfile(SecuritySupport.currentUserId()));
    }

    @PutMapping
    public ApiResponse<UserProfileResponse> update(@RequestBody @Valid UserUpdateRequest request) {
        return ApiResponse.ok(userAccountService.updateProfile(SecuritySupport.currentUserId(), request));
    }

    @PutMapping("/password")
    public ApiResponse<Boolean> updatePassword(@RequestBody @Valid PasswordUpdateRequest request) {
        userAccountService.updatePassword(SecuritySupport.currentUserId(), request);
        return ApiResponse.ok(Boolean.TRUE);
    }
}
