package dev.hrutvik.filesync.quota;
import jakarta.validation.constraints.NotBlank; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/storage/quota") @Validated public class TenantQuotaController {
 private final TenantQuotaService service; public TenantQuotaController(TenantQuotaService service){this.service=service;}
 @GetMapping TenantQuotaResponse get(@RequestHeader("X-Tenant-Id") @NotBlank String tenant){return service.get(tenant);}
}
