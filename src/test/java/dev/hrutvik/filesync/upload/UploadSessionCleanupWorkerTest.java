package dev.hrutvik.filesync.upload;

import dev.hrutvik.filesync.blob.MultipartBlobGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadSessionCleanupWorkerTest {
 @Test void abortsClaimedUploadsAndMarksThemComplete() {
  var store = mock(UploadSessionStore.class);
  var blobs = mock(MultipartBlobGateway.class);
  var metrics = new SimpleMeterRegistry();
  var session = session();
  when(store.claimExpired(anyString(), eq(10), eq(Duration.ofMinutes(5)))).thenReturn(List.of(session));

  new UploadSessionCleanupWorker(store, blobs, metrics, 10, Duration.ofMinutes(5)).cleanup();

  verify(blobs).abortIfPresent(session.objectKey(), session.remoteUploadId());
  verify(store).markAborted(eq(session.id()), anyString());
  assertEquals(1.0, metrics.counter("filesync.upload.cleanup", "result", "aborted").count());
 }

 @Test void releasesTheLeaseForRetryWhenObjectStorageFails() {
  var store = mock(UploadSessionStore.class);
  var blobs = mock(MultipartBlobGateway.class);
  var metrics = new SimpleMeterRegistry();
  var session = session();
  when(store.claimExpired(anyString(), anyInt(), any())).thenReturn(List.of(session));
  doThrow(new IllegalStateException("temporary S3 outage")).when(blobs).abortIfPresent(anyString(), anyString());

  new UploadSessionCleanupWorker(store, blobs, metrics, 10, Duration.ofMinutes(5)).cleanup();

  verify(store).releaseCleanup(eq(session.id()), anyString(), eq("temporary S3 outage"));
  verify(store, never()).markAborted(any(), anyString());
  assertEquals(1.0, metrics.counter("filesync.upload.cleanup", "result", "retry").count());
 }

 private static UploadSessionStore.Session session() {
  return new UploadSessionStore.Session(UUID.randomUUID(), "operation", "f".repeat(64), "a".repeat(64), 10, 1,
    "blobs/aa/" + "a".repeat(64), "remote-id", "ABORTING", null, Instant.now().minusSeconds(60));
 }
}
