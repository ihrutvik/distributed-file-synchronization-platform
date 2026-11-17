package dev.hrutvik.filesync.upload;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.http.*; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/v1/upload-sessions") @Validated public class UploadSessionController {
 private final UploadSessionService service; public UploadSessionController(UploadSessionService service){this.service=service;}
 @PostMapping ResponseEntity<UploadSessionResponse> create(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@Valid @RequestBody CreateUploadSessionRequest request){var result=service.create(tenant,request);return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);}
 @PostMapping("/{id}/complete") UploadSessionResponse complete(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@PathVariable UUID id,@Valid @RequestBody CompleteUploadSessionRequest request){return service.complete(tenant,id,request);}
}
