package com.lms.enrollmentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class OrganizationTest {
  @Test
  void anotherInstructorCannotReadOrganizationReports() {
    var jdbc = mock(JdbcTemplate.class);
    var service =
        new OrganizationService(
            jdbc,
            mock(com.lms.enrollmentservice.client.UserClient.class),
            mock(LearningManagementService.class),
            mock(CourseCatalogService.class),
            mock(OutboxService.class));
    UUID org = UUID.randomUUID(), group = UUID.randomUUID(), viewer = UUID.randomUUID();
    when(jdbc.queryForList(
            "select organization_id from training_groups where id=?", UUID.class, group))
        .thenReturn(List.of(org));
    when(jdbc.queryForList("select * from training_organizations where id=?", org))
        .thenReturn(List.of(Map.of("owner_id", UUID.randomUUID())));
    assertEquals(
        403,
        assertThrows(ResponseStatusException.class, () -> service.report(group, viewer, false, 0))
            .getStatusCode()
            .value());
  }

  @Test
  void exportsNeutralizeFormulasAndQuoteCsvText() {
    assertEquals("\"'=SUM(A1:A2)\"", OrganizationService.csvCell("=SUM(A1:A2)"));
    assertEquals("\"Tom, \"\"Learner\"\"\"", OrganizationService.csvCell("Tom, \"Learner\""));
    assertEquals("\"test\"", OrganizationService.csvCell("test"));
    assertEquals("\"'\tformula\"", OrganizationService.csvCell("\tformula"));
  }
}
