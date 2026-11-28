package dev.hrutvik.filesync.reconciliation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BlobReconciliationStore {
 private final JdbcClient db;
 public BlobReconciliationStore(JdbcClient db){this.db=db;}

 @Transactional
 public List<Candidate> claim(String owner,int limit,Duration lease,Duration interval){
  var rows=db.sql("select id,object_key,content_hash,size_bytes from upload_sessions where status='COMPLETED' and verification_status='VERIFIED' and (reconciled_at is null or reconciled_at<now()-(:interval_seconds*interval '1 second')) and (reconciliation_lease_until is null or reconciliation_lease_until<now()) order by reconciled_at nulls first,completed_at limit :limit for update skip locked")
    .param("interval_seconds",interval.toSeconds()).param("limit",limit)
    .query((rs,n)->new Candidate(rs.getObject("id",UUID.class),rs.getString("object_key"),rs.getString("content_hash"),rs.getLong("size_bytes"))).list();
  rows.forEach(row->db.sql("update upload_sessions set reconciliation_lease_owner=:owner,reconciliation_lease_until=now()+(:lease_seconds*interval '1 second') where id=:id")
    .param("owner",owner).param("lease_seconds",lease.toSeconds()).param("id",row.id()).update());
  return rows;
 }
 public void healthy(UUID id,String owner){finish(id,owner,"HEALTHY",null);}
 public void missing(UUID id,String owner,String reason){finish(id,owner,"MISSING",reason);}
 public void release(UUID id,String owner){db.sql("update upload_sessions set reconciliation_lease_owner=null,reconciliation_lease_until=null where id=:id and reconciliation_lease_owner=:owner").param("id",id).param("owner",owner).update();}
 private void finish(UUID id,String owner,String health,String reason){db.sql("update upload_sessions set blob_health=:health,blob_health_reason=:reason,reconciled_at=now(),reconciliation_lease_owner=null,reconciliation_lease_until=null where id=:id and reconciliation_lease_owner=:owner").param("health",health).param("reason",reason).param("id",id).param("owner",owner).update();}
 public record Candidate(UUID id,String objectKey,String expectedHash,long expectedSize){}
}
