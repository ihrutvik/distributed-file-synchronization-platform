package dev.hrutvik.filesync.quota;
import org.springframework.beans.factory.annotation.Value; import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.stereotype.Repository;
@Repository public class TenantQuotaStore {
 private final JdbcClient db; private final long defaultQuota;
 public TenantQuotaStore(JdbcClient db,@Value("${filesync.quota.default-bytes:10737418240}") long defaultQuota){this.db=db;this.defaultQuota=defaultQuota;}
 public State applyDelta(String tenant,long delta){ensure(tenant);var current=lock(tenant);long next=Math.addExact(current.usedBytes(),delta);if(next<0)throw new IllegalStateException("storage accounting cannot become negative");if(next>current.quotaBytes())throw new QuotaExceededException(current.usedBytes(),delta,current.quotaBytes());db.sql("update tenant_storage_quotas set used_bytes=:u,updated_at=now() where tenant_id=:t").param("u",next).param("t",tenant).update();return new State(next,current.quotaBytes());}
 public State get(String tenant){ensure(tenant);return db.sql("select used_bytes,quota_bytes from tenant_storage_quotas where tenant_id=:t").param("t",tenant).query((rs,n)->new State(rs.getLong("used_bytes"),rs.getLong("quota_bytes"))).single();}
 private void ensure(String tenant){db.sql("insert into tenant_storage_quotas(tenant_id,used_bytes,quota_bytes) values(:t,0,:q) on conflict do nothing").param("t",tenant).param("q",defaultQuota).update();}
 private State lock(String tenant){return db.sql("select used_bytes,quota_bytes from tenant_storage_quotas where tenant_id=:t for update").param("t",tenant).query((rs,n)->new State(rs.getLong("used_bytes"),rs.getLong("quota_bytes"))).single();}
 public record State(long usedBytes,long quotaBytes){}
}
