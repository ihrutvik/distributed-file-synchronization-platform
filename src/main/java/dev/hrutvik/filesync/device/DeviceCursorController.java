package dev.hrutvik.filesync.device;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/v1/sync/devices") @Validated public class DeviceCursorController {
 private final DeviceCursorService service; public DeviceCursorController(DeviceCursorService service){this.service=service;}
 @PutMapping("/{deviceId}/cursor") DeviceCursorResponse acknowledge(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@PathVariable UUID deviceId,@Valid @RequestBody AcknowledgeCursorRequest request){return service.acknowledge(tenant,deviceId,request.cursor());}
 @GetMapping("/{deviceId}/cursor") DeviceCursorResponse get(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@PathVariable UUID deviceId){return service.get(tenant,deviceId);}
 @GetMapping("/compaction-watermark") CompactionWatermarkResponse watermark(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@RequestParam(defaultValue="720") int activeWithinHours){return service.watermark(tenant,activeWithinHours);}
}

