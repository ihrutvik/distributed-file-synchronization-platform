package dev.hrutvik.filesync.upload;
import jakarta.validation.constraints.*;
public record CreateUploadSessionRequest(@NotBlank @Size(max=120) String operationId,@NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String contentHash,@Positive long sizeBytes,@Min(1) @Max(10000) int partCount) {}
