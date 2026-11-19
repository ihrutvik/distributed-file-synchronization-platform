package dev.hrutvik.filesync.api;
import dev.hrutvik.filesync.verification.BlobNotVerifiedException;
import dev.hrutvik.filesync.device.DeviceCursorConflictException;
import dev.hrutvik.filesync.version.VersionConflictException; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(VersionConflictException.class) ResponseEntity<?> conflict(VersionConflictException e){return ResponseEntity.status(409).body(Map.of("code","VERSION_CONFLICT","message",e.getMessage(),"timestamp",Instant.now().toString()));}
 @ExceptionHandler(BlobNotVerifiedException.class) ResponseEntity<?> unverified(BlobNotVerifiedException e){return ResponseEntity.status(409).body(Map.of("code","BLOB_NOT_VERIFIED","message",e.getMessage(),"timestamp",Instant.now().toString()));}
 @ExceptionHandler(DeviceCursorConflictException.class) ResponseEntity<?> cursorConflict(DeviceCursorConflictException e){return ResponseEntity.status(409).body(Map.of("code","DEVICE_CURSOR_REGRESSION","message",e.getMessage(),"timestamp",Instant.now().toString()));}
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_PATH","message",e.getMessage(),"timestamp",Instant.now().toString()));}
}

