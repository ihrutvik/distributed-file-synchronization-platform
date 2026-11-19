package dev.hrutvik.filesync.device;
import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.stereotype.Repository; import java.time.Instant; import java.util.*;
@Repository public class DeviceCursorStore {
 private final JdbcClient db; public DeviceCursorStore(JdbcClient db){this.db=db;}
 public Optional<State> lock(String tenant,UUID device){return db.sql("select device_id,acknowledged_cursor,last_seen_at from device_sync_cursors where tenant_id=:t and device_id=:d for update").param("t",tenant).param("d",device).query(this::map).optional();}
 public Optional<State> find(String tenant,UUID device){return db.sql("select device_id,acknowledged_cursor,last_seen_at from device_sync_cursors where tenant_id=:t and device_id=:d").param("t",tenant).param("d",device).query(this::map).optional();}
 public long tenantHead(String tenant){return db.sql("select coalesce(max(change_sequence),0) from file_versions where tenant_id=:t").param("t",tenant).query(Long.class).single();}
 public State insert(String tenant,UUID device,long cursor){db.sql("insert into device_sync_cursors(tenant_id,device_id,acknowledged_cursor) values(:t,:d,:c) on conflict(tenant_id,device_id) do update set acknowledged_cursor=greatest(device_sync_cursors.acknowledged_cursor,excluded.acknowledged_cursor),updated_at=now(),last_seen_at=now()").param("t",tenant).param("d",device).param("c",cursor).update();return find(tenant,device).orElseThrow();}
 public State advance(String tenant,UUID device,long cursor){db.sql("update device_sync_cursors set acknowledged_cursor=:c,updated_at=now(),last_seen_at=now() where tenant_id=:t and device_id=:d").param("c",cursor).param("t",tenant).param("d",device).update();return find(tenant,device).orElseThrow();}
 public Watermark watermark(String tenant,Instant activeSince){return db.sql("select coalesce(min(acknowledged_cursor),0) safe_cursor,count(*) device_count from device_sync_cursors where tenant_id=:t and last_seen_at>=:s").param("t",tenant).param("s",activeSince).query((rs,n)->new Watermark(rs.getLong("safe_cursor"),rs.getInt("device_count"))).single();}
 private State map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new State(rs.getObject("device_id",UUID.class),rs.getLong("acknowledged_cursor"),rs.getTimestamp("last_seen_at").toInstant());}
 public record State(UUID deviceId,long cursor,Instant lastSeenAt){} public record Watermark(long cursor,int activeDeviceCount){}
}

