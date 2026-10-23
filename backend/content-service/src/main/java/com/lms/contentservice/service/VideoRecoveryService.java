package com.lms.contentservice.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class VideoRecoveryService {

    private final JdbcTemplate jdbc;
    private final VideoProcessingService processor;

    @Scheduled(fixedDelayString = "${video.recovery-delay:60000}")
    public void recover() {
        for (var id : jdbc.queryForList(
            "select id from media_assets where type='VIDEO' and status='PROCESSING' order by created_at limit 10",
            UUID.class
        ))
            try {
                processor.processVideoAsync(id);
            } catch (org.springframework.core.task.TaskRejectedException e) {
                break;
            }
    }

    public List<Map<String, Object>> failed(UUID user, boolean admin) {
        return admin
            ? jdbc.queryForList(
                  "select id,original_file_name,status,failure_reason,updated_at from media_assets where type='VIDEO' and status in('FAILED','PROCESSING') order by updated_at desc limit 100"
              )
            : jdbc.queryForList(
                  "select id,original_file_name,status,failure_reason,updated_at from media_assets where owner_id=? and type='VIDEO' and status in('FAILED','PROCESSING') order by updated_at desc limit 100",
                  user
              );
    }

    public void retry(UUID id, UUID user, boolean admin) {
        int changed = admin
            ? jdbc.update(
                  "update media_assets set status='PROCESSING',failure_reason=null,updated_at=now() where id=? and type='VIDEO' and status='FAILED'",
                  id
              )
            : jdbc.update(
                  "update media_assets set status='PROCESSING',failure_reason=null,updated_at=now() where id=? and owner_id=? and type='VIDEO' and status='FAILED'",
                  id,
                  user
              );
        if (changed != 1) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Yalnızca size ait başarısız video yeniden işlenebilir"
        );
        try {
            processor.processVideoAsync(id);
        } catch (org.springframework.core.task.TaskRejectedException ignored) {
            /* Durable PROCESSING state is recovered by the scheduler. */
        }
    }
}
