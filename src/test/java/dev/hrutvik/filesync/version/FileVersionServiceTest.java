package dev.hrutvik.filesync.version;
import org.junit.jupiter.api.Test; import java.util.UUID; import static org.junit.jupiter.api.Assertions.*;
class FileVersionServiceTest {
 @Test void canonicalizesSafeRelativePaths(){assertEquals("docs/report.pdf",FileVersionService.normalize("docs//report.pdf"));}
 @Test void rejectsTraversalAndAbsolutePaths(){assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("../secret"));assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("/root.txt"));}
 @Test void fingerprintIsDeterministicAndPayloadAware(){var device=UUID.randomUUID();assertEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"a",1));assertNotEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"b",1));}
}
