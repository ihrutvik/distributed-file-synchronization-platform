package dev.hrutvik.filesync.change;
import jakarta.validation.constraints.*; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/changes") @Validated public class ChangeFeedController {
 private final ChangeFeedService service; public ChangeFeedController(ChangeFeedService service){this.service=service;}
 @GetMapping ChangeFeedResponse changes(@RequestHeader("X-Tenant-Id") @NotBlank String tenant,@RequestParam(defaultValue="0") @PositiveOrZero long after,@RequestParam(defaultValue="100") int limit){return service.changes(tenant,after,limit);}
}
