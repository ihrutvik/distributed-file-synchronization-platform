package dev.hrutvik.filesync.version;

import java.time.Instant;
import java.util.UUID;

public record FileManifestResponse(UUID fileId,long version,String path,String contentHash,long sizeBytes,Instant committedAt,boolean cached) {
 FileManifestResponse fromCache(){return new FileManifestResponse(fileId,version,path,contentHash,sizeBytes,committedAt,true);}
}
