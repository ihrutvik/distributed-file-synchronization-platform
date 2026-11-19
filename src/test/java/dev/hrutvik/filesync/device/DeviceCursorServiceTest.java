package dev.hrutvik.filesync.device;
import org.junit.jupiter.api.Test; import java.time.*; import java.util.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
class DeviceCursorServiceTest {
 @Test void advancesCursorMonotonically(){var store=mock(DeviceCursorStore.class);var device=UUID.randomUUID();var state=new DeviceCursorStore.State(device,12,Instant.now());when(store.tenantHead("acme")).thenReturn(20L);when(store.lock("acme",device)).thenReturn(Optional.of(new DeviceCursorStore.State(device,10,Instant.now())));when(store.advance("acme",device,12)).thenReturn(state);var response=new DeviceCursorService(store).acknowledge("acme",device,12);assertEquals(12,response.acknowledgedCursor());}
 @Test void rejectsCursorRegression(){var store=mock(DeviceCursorStore.class);var device=UUID.randomUUID();when(store.tenantHead("acme")).thenReturn(20L);when(store.lock("acme",device)).thenReturn(Optional.of(new DeviceCursorStore.State(device,10,Instant.now())));assertThrows(DeviceCursorConflictException.class,()->new DeviceCursorService(store).acknowledge("acme",device,9));verify(store,never()).advance(anyString(),any(),anyLong());}
 @Test void rejectsCursorAheadOfFeed(){var store=mock(DeviceCursorStore.class);when(store.tenantHead("acme")).thenReturn(5L);assertThrows(IllegalArgumentException.class,()->new DeviceCursorService(store).acknowledge("acme",UUID.randomUUID(),6));}
 @Test void calculatesActiveDeviceWatermark(){var store=mock(DeviceCursorStore.class);when(store.watermark(eq("acme"),any())).thenReturn(new DeviceCursorStore.Watermark(42,3));var result=new DeviceCursorService(store).watermark("acme",720);assertEquals(42,result.safeCursor());assertEquals(3,result.activeDeviceCount());}
}

