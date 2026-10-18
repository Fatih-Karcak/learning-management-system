package com.lms.courseservice.security;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String displayName, String role) implements Principal {
    @Override
    public String getName() {
        return id.toString();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
