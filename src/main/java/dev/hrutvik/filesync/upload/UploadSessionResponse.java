package dev.hrutvik.filesync.upload;
import dev.hrutvik.filesync.blob.MultipartBlobGateway.PartUpload; import java.time.Instant; import java.util.*;
public record UploadSessionResponse(UUID sessionId,String contentHash,long sizeBytes,int partCount,String status,String verificationStatus,Instant expiresAt,List<PartUpload> parts,boolean replayed) {}

