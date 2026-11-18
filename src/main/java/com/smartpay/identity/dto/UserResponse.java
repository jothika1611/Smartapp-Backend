package com.smartpay.identity.dto;

import java.util.Set;

/** Safe user view. Deliberately has no passwordHash field. */
public class UserResponse {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String phone;
    private final String status;
    private final Set<String> roles;

    public UserResponse(Long id, String email, String fullName, String phone,
                        String status, Set<String> roles) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.status = status;
        this.roles = roles;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }
    public Set<String> getRoles() { return roles; }
}
