package dev.hrutvik.filesync.upload;

import dev.hrutvik.filesync.blob.MultipartBlobGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class UploadSessionCleanupWorker {
 private final UploadSessionStore store;
 private final MultipartBlobGateway blobs;
 private final MeterRegistry metrics;
 private final String owner = UUID.randomUUID().toString();
 private final int batchSize;
 private final Duration leaseDuration;

 public UploadSessionCleanupWorker(UploadSessionStore store, MultipartBlobGateway blobs, MeterRegistry metrics,
   @Value("${filesync.upload.cleanup.batch-size:50}") int batchSize,
   @Value("${filesync.upload.cleanup.lease-duration:PT5M}") Duration leaseDuration) {
  this.store = store;
  this.blobs = blobs;
  this.metrics = metrics;
  this.batchSize = batchSize;
  this.leaseDuration = leaseDuration;
 }

 @Scheduled(fixedDelayString = "${filesync.upload.cleanup.poll-delay:60000}")
 public void cleanup() {
  for (var session : store.claimExpired(owner, batchSize, leaseDuration)) {
   try {
    blobs.abortIfPresent(session.objectKey(), session.remoteUploadId());
    store.markAborted(session.id(), owner);
    count("aborted");
   } catch (RuntimeException failure) {
    store.releaseCleanup(session.id(), owner, safeMessage(failure));
    count("retry");
   }
  }
 }

 private static String safeMessage(RuntimeException failure) {
  String message = failure.getMessage();
  if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
  return message.substring(0, Math.min(message.length(), 1000));
 }

 private void count(String result) {
  metrics.counter("filesync.upload.cleanup", "result", result).increment();
 }
}
