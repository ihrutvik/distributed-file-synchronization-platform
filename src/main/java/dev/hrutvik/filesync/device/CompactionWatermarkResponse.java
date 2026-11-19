package dev.hrutvik.filesync.device;
import java.time.Instant;
public record CompactionWatermarkResponse(long safeCursor,int activeDeviceCount,Instant activeSince) {}

