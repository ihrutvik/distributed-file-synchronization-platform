package dev.hrutvik.filesync.device;
import java.time.Instant; import java.util.UUID;
public record DeviceCursorResponse(UUID deviceId,long acknowledgedCursor,Instant lastSeenAt) {}

