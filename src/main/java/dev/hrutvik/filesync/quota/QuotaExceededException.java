package dev.hrutvik.filesync.quota;
public class QuotaExceededException extends RuntimeException {
 public QuotaExceededException(long used,long delta,long quota){super("storage quota exceeded; usedBytes="+used+", requestedDelta="+delta+", quotaBytes="+quota);}
}
