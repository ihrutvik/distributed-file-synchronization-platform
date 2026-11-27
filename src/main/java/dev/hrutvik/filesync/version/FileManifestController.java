package dev.hrutvik.filesync.version;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/files/metadata") @Validated
public class FileManifestController {
 private final FileManifestService service; public FileManifestController(FileManifestService service){this.service=service;}
 @GetMapping("/{*path}") FileManifestResponse get(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@PathVariable String path,@RequestParam(defaultValue="false") boolean strong){return service.get(tenant,path,strong);}
}
