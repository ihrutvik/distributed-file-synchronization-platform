package dev.hrutvik.filesync.version;

import dev.hrutvik.filesync.verification.BlobNotVerifiedException;
import dev.hrutvik.filesync.verification.BlobVerificationStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileVersionServiceTest {
 @Test void canonicalizesSafeRelativePaths(){assertEquals("docs/report.pdf",FileVersionService.normalize("docs//report.pdf"));}
 @Test void rejectsTraversalAndAbsolutePaths(){assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("../secret"));assertThrows(IllegalArgumentException.class,()->FileVersionService.normalize("/root.txt"));}
 @Test void fingerprintIsDeterministicAndPayloadAware(){var device=UUID.randomUUID();assertEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"a",1));assertNotEquals(FileVersionService.fingerprint("PUT","op",device,"a",1),FileVersionService.fingerprint("PUT","op",device,"b",1));}

 @Test void refusesToCommitUnverifiedBytes(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);String hash="a".repeat(64);
  when(versions.replay(eq("acme"),eq("op"),anyString())).thenReturn(Optional.empty());
  var request=new CommitVersionRequest("op",UUID.randomUUID(),"docs/a.txt",hash,10,null,null);
  assertThrows(BlobNotVerifiedException.class,()->new FileVersionService(versions,verification).commit("acme",request));
  verify(versions,never()).lockHead(anyString(),anyString());
 }

 @Test void createsDeterministicConflictCopyForStaleEdit(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);
  var device=UUID.fromString("11111111-1111-1111-1111-111111111111");String hash="a".repeat(64);
  when(versions.replay(eq("acme"),eq("op"),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);
  when(versions.lockHead("acme","docs/report.pdf")).thenReturn(Optional.of(new FileVersionStore.Head(UUID.randomUUID(),4,"b".repeat(64),20,false,Instant.now())));
  when(versions.lockHead(eq("acme"),contains("conflict-11111111"))).thenReturn(Optional.empty());
  var request=new CommitVersionRequest("op",device,"docs/report.pdf",hash,10,3L,ConflictPolicy.KEEP_BOTH);
  new FileVersionService(versions,verification).commit("acme",request);
  verify(versions).save(eq("acme"),any(),eq(1L),argThat(saved->saved.path().startsWith("docs/report (conflict-11111111-")&&saved.path().endsWith(").pdf")&&saved.baseVersion()==null),anyString());
 }

 @Test void rejectsStaleEditByDefault(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);String hash="a".repeat(64);
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);
  when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(new FileVersionStore.Head(UUID.randomUUID(),2,hash,10,false,Instant.now())));
  var request=new CommitVersionRequest("op",UUID.randomUUID(),"a.txt",hash,10,1L,null);
  assertThrows(VersionConflictException.class,()->new FileVersionService(versions,verification).commit("acme",request));
 }

 @Test void preservesExtensionInConflictPath(){
  String path=FileVersionService.conflictPath("docs/report.pdf",UUID.fromString("11111111-1111-1111-1111-111111111111"),"op");
  assertTrue(path.startsWith("docs/report (conflict-11111111-"));assertTrue(path.endsWith(").pdf"));
 }
}

