package com.lms.userservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class MfaServiceTest {

    @Test
    void implementsRfc6238Sha1VectorsWithSixDigits() {
        String key = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        long[] times = { 59, 1111111109, 1111111111, 1234567890, 2000000000L, 20000000000L };
        String[] expected = { "287082", "081804", "050471", "005924", "279037", "353130" };
        for (int i = 0; i < times.length; i++) assertEquals(expected[i], MfaService.totp(key, times[i] / 30));
    }

    @Test
    void setupStoresEncryptedSecretRatherThanRecoverablePlaintext() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new MfaService(jdbc);
        ReflectionTestUtils.setField(service, "encryptionKey", "unit-test-encryption-key-not-for-deployment");
        UUID id = UUID.randomUUID();
        var setup = service.setup(id, "test@example.test");
        assertEquals(32, setup.secret().length());
        var stored = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(startsWith("insert into user_mfa"), eq(id), stored.capture());
        assertFalse(stored.getValue().contains(setup.secret()));
        assertTrue(Base64.getDecoder().decode(stored.getValue()).length > 32);
        assertTrue(setup.uri().startsWith("otpauth://totp/"));
    }
}
