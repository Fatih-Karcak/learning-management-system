package com.lms.contentservice.service;

import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A PostgreSQL session lock survives commits and is automatically released after a worker crash. */
@Component
@RequiredArgsConstructor
public class VideoJobLock {

    private final DataSource dataSource;

    @FunctionalInterface
    public interface Lease extends AutoCloseable {
        void close();
    }

    public Lease acquire(UUID id) throws SQLException {
        Connection c = dataSource.getConnection();
        long key = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        try (var q = c.prepareStatement("select pg_try_advisory_lock(?)")) {
            q.setLong(1, key);
            try (var rows = q.executeQuery()) {
                rows.next();
                if (!rows.getBoolean(1)) {
                    c.close();
                    return null;
                }
            }
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return () -> {
            try (var q = c.prepareStatement("select pg_advisory_unlock(?)")) {
                q.setLong(1, key);
                q.execute();
            } catch (SQLException e) {
                try {
                    c.abort(Runnable::run);
                } catch (SQLException ignored) {}
            } finally {
                try {
                    c.close();
                } catch (SQLException ignored) {}
            }
        };
    }
}
