package com.lms.userservice.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AdminScopeTest {

    @Test
    void supportCannotPromoteUsersOrApproveCourses() {
        assertFalse(AdminScopeService.allows("SUPPORT", "/api/v1/users/admin/target", "PUT"));
        assertFalse(AdminScopeService.allows("SUPPORT", "/api/v1/courses/admin", "GET"));
        assertFalse(
            AdminScopeService.allows("SUPPORT", "/api/v1/courses/course/lessons/lesson/quiz/manage", "GET")
        );
        assertFalse(AdminScopeService.allows("SUPPORT", "/api/v1/courses/admin/course/decision", "POST"));
        assertFalse(AdminScopeService.allows("SUPPORT", "/api/v1/courses/course/workspace", "GET"));
        assertTrue(AdminScopeService.allows("SUPPORT", "/api/v1/users/admin/tickets", "GET"));
        assertTrue(AdminScopeService.allows("SUPPORT", "/api/v1/enrollments/admin/grant", "POST"));
    }

    @Test
    void moderatorsCannotReadUserDirectoryOrChangeEnrollments() {
        assertFalse(AdminScopeService.allows("MODERATION", "/api/v1/users/search", "GET"));
        assertFalse(AdminScopeService.allows("MODERATION", "/api/v1/enrollments/admin/grant", "POST"));
        assertTrue(
            AdminScopeService.allows("MODERATION", "/api/v1/courses/admin/course/workspace/decision", "POST")
        );
        assertFalse(AdminScopeService.allows("MODERATION", "/api/v1/media/resource", "DELETE"));
    }

    @Test
    void unknownScopesFailClosedAndPersonalSecurityRemainsAvailable() {
        assertFalse(AdminScopeService.allows("UNKNOWN", "/api/v1/courses/admin", "GET"));
        assertTrue(AdminScopeService.allows("SUPPORT", "/api/v1/users/me/mfa/setup", "POST"));
        assertTrue(AdminScopeService.allows("FULL", "/api/v1/users/search", "GET"));
    }
}
