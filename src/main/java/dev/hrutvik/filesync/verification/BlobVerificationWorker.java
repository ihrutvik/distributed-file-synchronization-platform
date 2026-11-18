package dev.hrutvik.filesync.verification;
import dev.hrutvik.filesync.blob.MultipartBlobGateway; import io.micrometer.core.instrument.MeterRegistry; import org.springframework.beans.factory.annotation.Value; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import java.time.Duration; import java.util.UUID;
@Component public class BlobVerificationWorker {
 private final BlobVerificationStore store; private final MultipartBlobGateway blobs; private final MeterRegistry metrics; private final String owner=UUID.randomUUID().toString(); private final int batch; private final Duration lease;
 public BlobVerificationWorker(BlobVerificationStore store,MultipartBlobGateway blobs,MeterRegistry metrics,@Value("${filesync.verification.batch-size:20}") int batch,@Value("${filesync.verification.lease-duration:PT5M}") Duration lease){this.store=store;this.blobs=blobs;this.metrics=metrics;this.batch=batch;this.lease=lease;}
 @Scheduled(fixedDelayString="${filesync.verification.poll-delay:2000}") public void verify(){for(var candidate:store.claim(owner,batch,lease)){try{if(candidate.expectedHash().equals(blobs.sha256(candidate.objectKey()))){store.verified(candidate.id(),owner);count("verified");}else{store.rejected(candidate.id(),owner,"content hash mismatch");count("rejected");}}catch(RuntimeException failure){store.release(candidate.id(),owner);count("retry");}}}
 private void count(String result){metrics.counter("filesync.blob.verification","result",result).increment();}
}

