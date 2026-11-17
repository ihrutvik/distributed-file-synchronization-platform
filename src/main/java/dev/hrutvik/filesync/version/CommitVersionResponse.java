package dev.hrutvik.filesync.version;
import java.time.Instant; import java.util.UUID;
public record CommitVersionResponse(UUID fileId,long version,String path,String contentHash,long sizeBytes,Instant committedAt,boolean replayed) {}
