package dev.hrutvik.filesync.version;
import dev.hrutvik.filesync.verification.*; import org.junit.jupiter.api.Test; import java.util.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
class FileVersionServiceTest {
 @Test void canonicalizesSafeRelativePaths(){assertEquals("docs/report.pdf",FileVersionService.normalize("docs//report.pdf"));}
 @Test void rejectsTraversalAndAbsolutePaths(){assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("../secret"));assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("/root.txt"));}
 @Test void fingerprintIsDeterministicAndPayloadAware(){var device=UUID.randomUUID();assertEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"a",1));assertNotEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"b",1));}
 @Test void refusesToCommitUnverifiedBytes(){var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);String hash="a".repeat(64);when(versions.replay(eq("acme"),eq("op"),anyString())).thenReturn(Optional.empty());var request=new CommitVersionRequest("op",UUID.randomUUID(),"docs/a.txt",hash,10,null);assertThrows(BlobNotVerifiedException.class,()->new FileVersionService(versions,verification).commit("acme",request));verify(versions,never()).lockHead(anyString(),anyString());}
}

