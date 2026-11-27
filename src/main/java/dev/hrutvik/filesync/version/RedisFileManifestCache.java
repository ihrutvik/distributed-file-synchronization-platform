package dev.hrutvik.filesync.version;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisFileManifestCache implements FileManifestCache {
 private final StringRedisTemplate redis; private final ObjectMapper json; private final MeterRegistry metrics; private final Duration ttl;
 public RedisFileManifestCache(StringRedisTemplate redis,ObjectMapper json,MeterRegistry metrics,@Value("${filesync.manifest-cache.ttl:PT30S}") Duration ttl){this.redis=redis;this.json=json;this.metrics=metrics;this.ttl=ttl;}
 public Optional<FileManifestResponse> get(String tenant,String path){try{String value=redis.opsForValue().get(key(tenant,path));if(value==null){count("miss");return Optional.empty();}count("hit");return Optional.of(json.readValue(value,FileManifestResponse.class).fromCache());}catch(Exception failure){count("failure");return Optional.empty();}}
 public void put(String tenant,String path,FileManifestResponse manifest){try{redis.opsForValue().set(key(tenant,path),json.writeValueAsString(manifest),ttl);}catch(Exception failure){count("failure");}}
 public void evictAfterCommit(String tenant,String... paths){Runnable action=()->{try{for(String path:paths)redis.delete(key(tenant,path));}catch(RuntimeException failure){count("failure");}};if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});else action.run();}
 private String key(String tenant,String path){return "filesync:manifest:"+FileVersionService.fingerprint(tenant,path);}
 private void count(String result){metrics.counter("filesync.manifest.cache","result",result).increment();}
}
