package com.openmd.server.push.repository;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PushRetentionStore {

  private final JdbcTemplate jdbc;

  public PushRetentionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int deleteDeliveriesCreatedBefore(Instant cutoff, int limit) {
    return deleteLimited(
        "push_deliveries", "created_at", "created_at <", epochMicros(cutoff), limit);
  }

  public int deleteOperationsExpiredAtOrBefore(Instant cutoff, int limit) {
    return deleteLimited(
        "push_device_operations", "expires_at", "expires_at <=", epochMicros(cutoff), limit);
  }

  public int deleteInactiveDevicesBefore(Instant cutoff, int limit) {
    return jdbc.update(
        """
        DELETE FROM push_devices
        WHERE status IN ('DISABLED', 'REVOKED')
          AND inactive_at < TIMESTAMPADD(
            MICROSECOND, ?, '1970-01-01 00:00:00.000000'
          )
          AND id IN (
          SELECT id FROM (
            SELECT id FROM push_devices
            WHERE status IN ('DISABLED', 'REVOKED')
              AND inactive_at < TIMESTAMPADD(
                MICROSECOND, ?, '1970-01-01 00:00:00.000000'
              )
            ORDER BY inactive_at, id
            LIMIT ?
          ) expired_devices
        )
        """,
        epochMicros(cutoff),
        epochMicros(cutoff),
        limit);
  }

  private int deleteLimited(
      String table, String orderColumn, String comparison, long cutoffMicros, int limit) {
    String sql =
        "DELETE FROM "
            + table
            + " WHERE id IN (SELECT id FROM (SELECT id FROM "
            + table
            + " WHERE "
            + comparison
            + " TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000') ORDER BY "
            + orderColumn
            + ", id LIMIT ?) expired_rows)";
    return jdbc.update(sql, cutoffMicros, limit);
  }

  private long epochMicros(Instant value) {
    return Math.addExact(
        Math.multiplyExact(value.getEpochSecond(), 1_000_000L), value.getNano() / 1_000L);
  }
}
