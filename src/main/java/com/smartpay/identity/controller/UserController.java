package com.smartpay.identity.controller;

import com.smartpay.identity.dto.ChangePasswordRequest;
import com.smartpay.identity.dto.ProfileUpdateRequest;
import com.smartpay.identity.dto.UserResponse;
import com.smartpay.identity.security.CustomUserPrincipal;
import com.smartpay.identity.service.UserService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Simulated user profile and ADMIN-only user administration")
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the authenticated user's own profile")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping({"/profile", "/users/me"})
    public ApiResponse<UserResponse> myProfile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ApiResponse.ok(userService.getCurrentUser(principal.getId()));
    }

    @Operation(summary = "Update the authenticated user's own profile")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping({"/profile", "/users/me"})
    public ApiResponse<UserResponse> updateMyProfile(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok("Simulated profile updated.",
                userService.updateProfile(principal.getId(), request));
    }

    @Operation(summary = "Change the authenticated user's own password")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping({"/profile/change-password", "/users/me/change-password"})
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal CustomUserPrincipal principal,
                                             @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getId(), request);
        return ApiResponse.ok("Simulated password updated.", null);
    }

    @Operation(summary = "ADMIN only: list all users")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> listUsers(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(userService.listUsers(pageable)));
    }

    @Operation(summary = "ADMIN only: get any user by id")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/users/{id}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUser(id));
    }
}
