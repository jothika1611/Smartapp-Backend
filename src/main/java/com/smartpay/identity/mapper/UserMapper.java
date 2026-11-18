package com.smartpay.identity.mapper;

import com.smartpay.identity.dto.UserResponse;
import com.smartpay.identity.entity.Role;
import com.smartpay.identity.entity.User;

import java.util.Set;
import java.util.stream.Collectors;

/** Converts entities to DTOs. Never copies the password hash. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus().name(),
                roles
        );
    }
}
