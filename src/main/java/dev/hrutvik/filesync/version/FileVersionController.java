package dev.hrutvik.filesync.version;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.http.*; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/files") @Validated public class FileVersionController {
 private final FileVersionService service; public FileVersionController(FileVersionService service){this.service=service;}
 @PostMapping("/versions") ResponseEntity<CommitVersionResponse> commit(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@Valid @RequestBody CommitVersionRequest request){var result=service.commit(tenant,request);return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);}
 @PostMapping("/{*path}/delete") ResponseEntity<CommitVersionResponse> delete(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@PathVariable String path,@Valid @RequestBody DeleteFileRequest request){var result=service.delete(tenant,path,request);return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);}
 @PostMapping("/moves") ResponseEntity<CommitVersionResponse> move(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@Valid @RequestBody MoveFileRequest request){var result=service.move(tenant,request);return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);}
}
