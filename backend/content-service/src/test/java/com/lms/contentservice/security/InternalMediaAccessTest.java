package com.lms.contentservice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lms.contentservice.controller.InternalMediaController;
import com.lms.contentservice.service.MediaService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalMediaAccessTest {

    @Test
    void metadataRequiresTheInternalKey() {
        var media = mock(MediaService.class);
        UUID id = UUID.randomUUID();
        var controller = new InternalMediaController(media, "test-internal-key");
        assertThrows(ResponseStatusException.class, () -> controller.metadata(id, null));
        verifyNoInteractions(media);
        assertTrue(controller.metadata(id, "test-internal-key").isSuccess());
        verify(media).getInternalMetadata(id);
    }

    @Test
    void missingWrongAndUnconfiguredKeysCannotLinkMedia() {
        var media = mock(MediaService.class);
        var request = new InternalMediaController.LinkRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        for (String configured : new String[] { "", "test-internal-key" }) {
            var controller = new InternalMediaController(media, configured);
            for (String provided : new String[] { null, "", "wrong" }) {
                var error = assertThrows(ResponseStatusException.class, () ->
                    controller.linkVideo(UUID.randomUUID(), provided, request)
                );
                assertEquals(401, error.getStatusCode().value());
            }
        }
        verifyNoInteractions(media);
    }

    @Test
    void validInternalKeyPassesVerifiedCourseOwnerAndLesson() {
        var media = mock(MediaService.class);
        var controller = new InternalMediaController(media, "test-internal-key");
        UUID id = UUID.randomUUID();
        var request = new InternalMediaController.LinkRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        );
        assertTrue(controller.linkVideo(id, "test-internal-key", request).isSuccess());
        verify(media).linkVideoToLesson(id, request.courseId(), request.lessonId(), request.ownerId());
    }
}
