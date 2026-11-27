package dev.hrutvik.filesync.version;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileManifestServiceTest {
 @Test void returnsAHotManifestWithoutReadingPostgres(){var store=mock(FileVersionStore.class);var cache=mock(FileManifestCache.class);var cached=new FileManifestResponse(UUID.randomUUID(),3,"docs/a.txt","a".repeat(64),20,Instant.now(),true);when(cache.get("acme","docs/a.txt")).thenReturn(Optional.of(cached));assertSame(cached,new FileManifestService(store,cache).get("acme","docs/a.txt",false));verifyNoInteractions(store);}
 @Test void strongReadsBypassRedisAndRefreshTheCache(){var store=mock(FileVersionStore.class);var cache=mock(FileManifestCache.class);var head=new FileVersionStore.Head(UUID.randomUUID(),4,"a".repeat(64),20,false,Instant.now());when(store.findHead("acme","docs/a.txt")).thenReturn(Optional.of(head));var result=new FileManifestService(store,cache).get("acme","docs/a.txt",true);assertFalse(result.cached());verify(cache,never()).get(anyString(),anyString());verify(cache).put("acme","docs/a.txt",result);}
 @Test void deletedHeadsAreNotReturnedOrCached(){var store=mock(FileVersionStore.class);var cache=mock(FileManifestCache.class);var head=new FileVersionStore.Head(UUID.randomUUID(),5,"a".repeat(64),0,true,Instant.now());when(cache.get(anyString(),anyString())).thenReturn(Optional.empty());when(store.findHead("acme","docs/a.txt")).thenReturn(Optional.of(head));assertThrows(FileManifestNotFoundException.class,()->new FileManifestService(store,cache).get("acme","docs/a.txt",false));verify(cache,never()).put(anyString(),anyString(),any());}
}
