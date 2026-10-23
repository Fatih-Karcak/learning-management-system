package com.lms.enrollmentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.enrollmentservice.client.UserClient;
import com.lms.enrollmentservice.dto.response.CourseSummaryResponse;
import com.lms.enrollmentservice.mapper.EnrollmentMapper;
import com.lms.enrollmentservice.repository.EnrollmentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class FreeEnrollmentPolicyTest {

    @Test
    void paidCourseCannotBeAccessedThroughFreeEnrollmentEndpoint() {
        var catalog = mock(CourseCatalogService.class);
        var jdbc = mock(JdbcTemplate.class);
        var repository = mock(EnrollmentRepository.class);
        var service = new LearningManagementService(
            repository,
            mock(EnrollmentMapper.class),
            catalog,
            mock(UserClient.class),
            mock(OutboxService.class),
            jdbc
        );
        UUID id = UUID.randomUUID();
        when(catalog.requireCourse(id)).thenReturn(
            new CourseSummaryResponse(id, "Paid", null, "Teacher", new BigDecimal("100"), "PUBLISHED", null)
        );
        var failure = assertThrows(ResponseStatusException.class, () ->
            service.enrollFree(id, UUID.randomUUID())
        );
        assertEquals(409, failure.getStatusCode().value());
        verifyNoInteractions(repository, jdbc);
    }
}
