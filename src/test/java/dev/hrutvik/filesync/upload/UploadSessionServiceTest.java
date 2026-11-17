package dev.hrutvik.filesync.upload;
import org.junit.jupiter.api.Test; import java.util.List; import static org.junit.jupiter.api.Assertions.*;
class UploadSessionServiceTest {
 @Test void requiresEveryPartExactlyOnce(){var valid=new CompleteUploadSessionRequest(List.of(new CompleteUploadSessionRequest.CompletedPart(1,"a"),new CompleteUploadSessionRequest.CompletedPart(2,"b")));assertDoesNotThrow(()->UploadSessionService.validateParts(2,valid));var duplicate=new CompleteUploadSessionRequest(List.of(new CompleteUploadSessionRequest.CompletedPart(1,"a"),new CompleteUploadSessionRequest.CompletedPart(1,"b")));assertThrows(IllegalArgumentException.class,()->UploadSessionService.validateParts(2,duplicate));}
 @Test void rejectsOutOfRangeParts(){var invalid=new CompleteUploadSessionRequest(List.of(new CompleteUploadSessionRequest.CompletedPart(1,"a"),new CompleteUploadSessionRequest.CompletedPart(3,"b")));assertThrows(IllegalArgumentException.class,()->UploadSessionService.validateParts(2,invalid));}
}
