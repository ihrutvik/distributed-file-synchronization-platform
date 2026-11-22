package dev.hrutvik.filesync.change;
import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.stereotype.Repository; import java.util.List;
@Repository public class ChangeFeedStore {
 private final JdbcClient db; public ChangeFeedStore(JdbcClient db){this.db=db;}
 public List<ChangeItem> after(String tenant,long cursor,int limit){return db.sql("select change_sequence,file_id,version,path,previous_path,change_type,content_hash,size_bytes,deleted,committed_at from file_versions where tenant_id=:t and change_sequence>:c order by change_sequence limit :l").param("t",tenant).param("c",cursor).param("l",limit).query((rs,n)->new ChangeItem(rs.getLong("change_sequence"),rs.getObject("file_id",java.util.UUID.class),rs.getLong("version"),rs.getString("path"),rs.getString("previous_path"),rs.getString("change_type"),rs.getString("content_hash"),rs.getLong("size_bytes"),rs.getBoolean("deleted"),rs.getTimestamp("committed_at").toInstant())).list();}
}
