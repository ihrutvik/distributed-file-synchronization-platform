package dev.hrutvik.filesync.device;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.*; import java.util.UUID;
@Service public class DeviceCursorService {
 private final DeviceCursorStore store; public DeviceCursorService(DeviceCursorStore store){this.store=store;}
 @Transactional public DeviceCursorResponse acknowledge(String tenant,UUID device,long cursor){long head=store.tenantHead(tenant);if(cursor>head)throw new IllegalArgumentException("cursor is ahead of the tenant change feed; tenantHead="+head);var current=store.lock(tenant,device);if(current.isPresent()&&cursor<current.get().cursor())throw new DeviceCursorConflictException("cursor cannot move backwards; current="+current.get().cursor()+", supplied="+cursor);var state=current.isEmpty()?store.insert(tenant,device,cursor):store.advance(tenant,device,cursor);return response(state);}
 @Transactional(readOnly=true) public DeviceCursorResponse get(String tenant,UUID device){return store.find(tenant,device).map(this::response).orElse(new DeviceCursorResponse(device,0,null));}
 @Transactional(readOnly=true) public CompactionWatermarkResponse watermark(String tenant,int activeWithinHours){if(activeWithinHours<1||activeWithinHours>8760)throw new IllegalArgumentException("activeWithinHours must be between 1 and 8760");var since=Instant.now().minus(Duration.ofHours(activeWithinHours));var result=store.watermark(tenant,since);return new CompactionWatermarkResponse(result.cursor(),result.activeDeviceCount(),since);}
 private DeviceCursorResponse response(DeviceCursorStore.State state){return new DeviceCursorResponse(state.deviceId(),state.cursor(),state.lastSeenAt());}
}

