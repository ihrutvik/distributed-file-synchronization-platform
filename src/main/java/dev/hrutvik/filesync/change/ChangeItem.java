package dev.hrutvik.filesync.change;
import java.time.Instant; import java.util.UUID;
public record ChangeItem(long cursor,UUID fileId,long version,String path,String previousPath,String changeType,String contentHash,long sizeBytes,boolean deleted,Instant committedAt) {}
