package dev.hrutvik.filesync.reconciliation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import dev.hrutvik.filesync.blob.MultipartBlobGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class BlobReconciliationWorkerTest {
 private static BlobReconciliationWorker worker(BlobReconciliationStore store,MultipartBlobGateway blobs){return new BlobReconciliationWorker(store,blobs,new SimpleMeterRegistry(),10,Duration.ofMinutes(5),Duration.ofHours(24));}
 @Test void marksMissingObject(){var store=mock(BlobReconciliationStore.class);var blobs=mock(MultipartBlobGateway.class);var id=UUID.randomUUID();when(store.claim(anyString(),eq(10),any(),any())).thenReturn(List.of(new BlobReconciliationStore.Candidate(id,"blob", "a".repeat(64),12)));when(blobs.headIfPresent("blob")).thenReturn(Optional.empty());worker(store,blobs).reconcile();verify(store).missing(eq(id),anyString(),contains("missing"));}
 @Test void restoresHealthyObject(){var store=mock(BlobReconciliationStore.class);var blobs=mock(MultipartBlobGateway.class);var id=UUID.randomUUID();var hash="a".repeat(64);when(store.claim(anyString(),eq(10),any(),any())).thenReturn(List.of(new BlobReconciliationStore.Candidate(id,"blob",hash,12)));when(blobs.headIfPresent("blob")).thenReturn(Optional.of(new MultipartBlobGateway.BlobHead(12,hash)));worker(store,blobs).reconcile();verify(store).healthy(eq(id),anyString());}
 @Test void releasesLeaseOnStorageFailure(){var store=mock(BlobReconciliationStore.class);var blobs=mock(MultipartBlobGateway.class);var id=UUID.randomUUID();when(store.claim(anyString(),eq(10),any(),any())).thenReturn(List.of(new BlobReconciliationStore.Candidate(id,"blob","a".repeat(64),12)));when(blobs.headIfPresent("blob")).thenThrow(new IllegalStateException());worker(store,blobs).reconcile();verify(store).release(eq(id),anyString());}
}
