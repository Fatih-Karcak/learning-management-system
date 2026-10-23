package com.lms.contentservice.event;

import com.lms.contentservice.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessingListener {

    private final VideoProcessingService videoProcessingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoProcessingRequested event) {
        log.info("Medya kaydı commit edildi, video işleme başlatılıyor: {}", event.mediaId());

        try {
            videoProcessingService.processVideoAsync(event.mediaId());
        } catch (org.springframework.core.task.TaskRejectedException e) {
            log.info("Video kuyrukta bekleyecek: {}", event.mediaId());
        }
    }
}
