package dev.hrutvik.filesync.version;
import jakarta.validation.constraints.*; import java.util.UUID;
public record CommitVersionRequest(@NotBlank @Size(max=120) String operationId,@NotNull UUID deviceId,@NotBlank @Size(max=1024) String path,@NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String contentHash,@PositiveOrZero long sizeBytes,Long baseVersion) {}
