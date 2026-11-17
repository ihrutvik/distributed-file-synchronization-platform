package dev.hrutvik.filesync.upload;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.List;
public record CompleteUploadSessionRequest(@NotEmpty @Size(max=10000) List<@Valid CompletedPart> parts){public record CompletedPart(@Min(1) int partNumber,@NotBlank String eTag){}}
