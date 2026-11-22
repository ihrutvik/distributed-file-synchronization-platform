package dev.hrutvik.filesync.version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MoveFileRequest(
  @NotBlank @Size(max = 120) String operationId,
  @NotNull UUID deviceId,
  @NotBlank @Size(max = 1024) String sourcePath,
  @NotBlank @Size(max = 1024) String destinationPath,
  @NotNull Long baseVersion
) {}
