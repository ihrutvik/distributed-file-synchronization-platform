package dev.hrutvik.filesync.upload;
import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.stereotype.Repository; import java.time.Instant; import java.util.*;
@Repository public class UploadSessionStore {
 private final JdbcClient db; public UploadSessionStore(JdbcClient db){this.db=db;}
 public Optional<Session> findByOperation(String tenant,String operation){return db.sql("select * from upload_sessions where tenant_id=:t and operation_id=:o").param("t",tenant).param("o",operation).query(this::map).optional();}
 public Optional<Session> lock(UUID id,String tenant){return db.sql("select * from upload_sessions where id=:id and tenant_id=:t for update").param("id",id).param("t",tenant).query(this::map).optional();}
 public Optional<Session> find(UUID id,String tenant){return db.sql("select * from upload_sessions where id=:id and tenant_id=:t").param("id",id).param("t",tenant).query(this::map).optional();}
 public Session insert(String tenant,String operation,String fingerprint,String hash,long size,int count,String key,String remoteId,Instant expires){UUID id=UUID.randomUUID();db.sql("insert into upload_sessions(id,tenant_id,operation_id,request_fingerprint,content_hash,size_bytes,part_count,object_key,remote_upload_id,status,created_at,expires_at) values(:id,:t,:o,:f,:h,:s,:c,:k,:r,'READY',now(),:e)").params(Map.of("id",id,"t",tenant,"o",operation,"f",fingerprint,"h",hash,"s",size,"c",count,"k",key,"r",remoteId,"e",expires)).update();return lock(id,tenant).orElseThrow();}
 public void markCompleted(UUID id){db.sql("update upload_sessions set status='COMPLETED',completed_at=now() where id=:id and status='READY'").param("id",id).update();}
 private Session map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Session(rs.getObject("id",UUID.class),rs.getString("operation_id"),rs.getString("request_fingerprint"),rs.getString("content_hash"),rs.getLong("size_bytes"),rs.getInt("part_count"),rs.getString("object_key"),rs.getString("remote_upload_id"),rs.getString("status"),rs.getString("verification_status"),rs.getTimestamp("expires_at").toInstant());}
 public record Session(UUID id,String operationId,String fingerprint,String hash,long sizeBytes,int partCount,String objectKey,String remoteUploadId,String status,String verificationStatus,Instant expiresAt){}
}

