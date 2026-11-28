package dev.hrutvik.filesync.reconciliation;

import dev.hrutvik.filesync.blob.MultipartBlobGateway;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlobReconciliationWorker {
 private final BlobReconciliationStore store; private final MultipartBlobGateway blobs; private final MeterRegistry metrics;
 private final String owner=UUID.randomUUID().toString(); private final int batch; private final Duration lease; private final Duration interval;
 public BlobReconciliationWorker(BlobReconciliationStore store,MultipartBlobGateway blobs,MeterRegistry metrics,
  @Value("${filesync.reconciliation.batch-size:50}") int batch,@Value("${filesync.reconciliation.lease-duration:PT5M}") Duration lease,
  @Value("${filesync.reconciliation.interval:PT24H}") Duration interval){this.store=store;this.blobs=blobs;this.metrics=metrics;this.batch=batch;this.lease=lease;this.interval=interval;}
 @Scheduled(fixedDelayString="${filesync.reconciliation.poll-delay:60000}")
 public void reconcile(){for(var candidate:store.claim(owner,batch,lease,interval)){try{var head=blobs.headIfPresent(candidate.objectKey());if(head.isEmpty()){store.missing(candidate.id(),owner,"object missing from blob storage");count("missing");continue;}var actual=head.get();if(actual.sizeBytes()!=candidate.expectedSize()||!candidate.expectedHash().equals(actual.declaredHash())){store.missing(candidate.id(),owner,"object metadata does not match verified blob");count("mismatch");}else{store.healthy(candidate.id(),owner);count("healthy");}}catch(RuntimeException failure){store.release(candidate.id(),owner);count("retry");}}}
 private void count(String result){metrics.counter("filesync.blob.reconciliation","result",result).increment();}
}
