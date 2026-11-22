package dev.hrutvik.filesync.version;

import dev.hrutvik.filesync.quota.QuotaExceededException;
import dev.hrutvik.filesync.quota.TenantQuotaStore;
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
  assertThrows(BlobNotVerifiedException.class,()->new FileVersionService(versions,verification,mock(TenantQuotaStore.class)).commit("acme",request));
  verify(versions,never()).lockHead(anyString(),anyString());
 }

 @Test void createsDeterministicConflictCopyForStaleEdit(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);
  var device=UUID.fromString("11111111-1111-1111-1111-111111111111");String hash="a".repeat(64);
  when(versions.replay(eq("acme"),eq("op"),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);
  when(versions.lockHead("acme","docs/report.pdf")).thenReturn(Optional.of(new FileVersionStore.Head(UUID.randomUUID(),4,"b".repeat(64),20,false,Instant.now())));
  when(versions.lockHead(eq("acme"),contains("conflict-11111111"))).thenReturn(Optional.empty());
  var request=new CommitVersionRequest("op",device,"docs/report.pdf",hash,10,3L,ConflictPolicy.KEEP_BOTH);
  var quota=mock(TenantQuotaStore.class);
  new FileVersionService(versions,verification,quota).commit("acme",request);
  verify(quota).applyDelta("acme",10);
  verify(versions).save(eq("acme"),any(),eq(1L),argThat(saved->saved.path().startsWith("docs/report (conflict-11111111-")&&saved.path().endsWith(").pdf")&&saved.baseVersion()==null),anyString());
 }

 @Test void rejectsStaleEditByDefault(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);String hash="a".repeat(64);
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);
  when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(new FileVersionStore.Head(UUID.randomUUID(),2,hash,10,false,Instant.now())));
  var request=new CommitVersionRequest("op",UUID.randomUUID(),"a.txt",hash,10,1L,null);
  assertThrows(VersionConflictException.class,()->new FileVersionService(versions,verification,mock(TenantQuotaStore.class)).commit("acme",request));
 }

 @Test void accountsOnlyForTheLogicalSizeDeltaOnUpdate(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);var quota=mock(TenantQuotaStore.class);String hash="a".repeat(64);var fileId=UUID.randomUUID();
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);
  when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(new FileVersionStore.Head(fileId,2,"b".repeat(64),20,false,Instant.now())));
  var request=new CommitVersionRequest("op",UUID.randomUUID(),"a.txt",hash,35,2L,null);
  new FileVersionService(versions,verification,quota).commit("acme",request);
  verify(quota).applyDelta("acme",15);
 }

 @Test void releasesLogicalBytesWhenAFileIsDeleted(){
  var versions=mock(FileVersionStore.class);var quota=mock(TenantQuotaStore.class);var head=new FileVersionStore.Head(UUID.randomUUID(),3,"a".repeat(64),35,false,Instant.now());
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(head));
  var request=new DeleteFileRequest("op",UUID.randomUUID(),3L);
  new FileVersionService(versions,mock(BlobVerificationStore.class),quota).delete("acme","a.txt",request);
  verify(quota).applyDelta("acme",-35);
 }

 @Test void doesNotPersistMetadataWhenQuotaIsExceeded(){
  var versions=mock(FileVersionStore.class);var verification=mock(BlobVerificationStore.class);var quota=mock(TenantQuotaStore.class);String hash="a".repeat(64);
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(verification.isVerified(hash)).thenReturn(true);when(versions.lockHead("acme","a.txt")).thenReturn(Optional.empty());
  doThrow(new QuotaExceededException(95,10,100)).when(quota).applyDelta("acme",10);
  var request=new CommitVersionRequest("op",UUID.randomUUID(),"a.txt",hash,10,null,null);
  assertThrows(QuotaExceededException.class,()->new FileVersionService(versions,verification,quota).commit("acme",request));
  verify(versions,never()).save(anyString(),any(),anyLong(),any(),anyString());
 }

 @Test void movesAFileWithoutChangingItsIdentityOrQuota(){
  var versions=mock(FileVersionStore.class);var quota=mock(TenantQuotaStore.class);var fileId=UUID.randomUUID();var head=new FileVersionStore.Head(fileId,4,"a".repeat(64),35,false,Instant.now());
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(versions.lockHead("acme","drafts/a.txt")).thenReturn(Optional.of(head));when(versions.lockHead("acme","published/a.txt")).thenReturn(Optional.empty());
  var request=new MoveFileRequest("move-op",UUID.randomUUID(),"drafts/a.txt","published/a.txt",4L);
  new FileVersionService(versions,mock(BlobVerificationStore.class),quota).move("acme",request);
  verify(versions).lockMovePaths("acme","drafts/a.txt","published/a.txt");
  verify(versions).move(eq("acme"),eq(head),eq("drafts/a.txt"),eq("published/a.txt"),eq(request),anyString());
  verifyNoInteractions(quota);
 }

 @Test void rejectsMoveWhenDestinationAlreadyExists(){
  var versions=mock(FileVersionStore.class);var source=new FileVersionStore.Head(UUID.randomUUID(),2,"a".repeat(64),10,false,Instant.now());var destination=new FileVersionStore.Head(UUID.randomUUID(),7,"b".repeat(64),20,false,Instant.now());
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(source));when(versions.lockHead("acme","b.txt")).thenReturn(Optional.of(destination));
  var request=new MoveFileRequest("move-op",UUID.randomUUID(),"a.txt","b.txt",2L);
  assertThrows(VersionConflictException.class,()->new FileVersionService(versions,mock(BlobVerificationStore.class),mock(TenantQuotaStore.class)).move("acme",request));
  verify(versions,never()).move(anyString(),any(),anyString(),anyString(),any(),anyString());
 }

 @Test void rejectsMoveWithStaleSourceVersion(){
  var versions=mock(FileVersionStore.class);var source=new FileVersionStore.Head(UUID.randomUUID(),3,"a".repeat(64),10,false,Instant.now());
  when(versions.replay(anyString(),anyString(),anyString())).thenReturn(Optional.empty());when(versions.lockHead("acme","a.txt")).thenReturn(Optional.of(source));
  var request=new MoveFileRequest("move-op",UUID.randomUUID(),"a.txt","b.txt",2L);
  assertThrows(VersionConflictException.class,()->new FileVersionService(versions,mock(BlobVerificationStore.class),mock(TenantQuotaStore.class)).move("acme",request));
 }

 @Test void preservesExtensionInConflictPath(){
  String path=FileVersionService.conflictPath("docs/report.pdf",UUID.fromString("11111111-1111-1111-1111-111111111111"),"op");
  assertTrue(path.startsWith("docs/report (conflict-11111111-"));assertTrue(path.endsWith(").pdf"));
 }
}
