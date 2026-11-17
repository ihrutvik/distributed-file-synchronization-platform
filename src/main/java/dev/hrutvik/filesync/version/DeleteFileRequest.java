package dev.hrutvik.filesync.version;
import jakarta.validation.constraints.*; import java.util.UUID;
public record DeleteFileRequest(@NotBlank @Size(max=120) String operationId,@NotNull UUID deviceId,Long baseVersion) {}
