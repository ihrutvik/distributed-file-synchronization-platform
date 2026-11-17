package dev.hrutvik.filesync.change;
import org.junit.jupiter.api.Test; import java.time.Instant; import java.util.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
class ChangeFeedServiceTest {
 @Test void returnsStableCursorAndDetectsMoreRows(){var store=mock(ChangeFeedStore.class);var first=new ChangeItem(11,UUID.randomUUID(),1,"a","a".repeat(64),1,false,Instant.now());var second=new ChangeItem(12,UUID.randomUUID(),1,"b","b".repeat(64),1,false,Instant.now());when(store.after("tenant",10,2)).thenReturn(List.of(first,second));var page=new ChangeFeedService(store).changes("tenant",10,1);assertEquals(1,page.changes().size());assertEquals(11,page.nextCursor());assertTrue(page.hasMore());}
 @Test void rejectsUnboundedPages(){assertThrows(IllegalArgumentException.class,()->ChangeFeedService.normalizeLimit(0));assertThrows(IllegalArgumentException.class,()->ChangeFeedService.normalizeLimit(501));}
}
