package dev.hrutvik.filesync.outbox;
import java.time.Instant; import java.util.UUID;
public record OutboxMessage(UUID id,String tenantId,UUID aggregateId,String eventType,String payload,Instant createdAt) {}
