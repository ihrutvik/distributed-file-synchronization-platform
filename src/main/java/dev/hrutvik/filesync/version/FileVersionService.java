package dev.hrutvik.filesync.version;

import dev.hrutvik.filesync.quota.TenantQuotaStore;
import dev.hrutvik.filesync.verification.BlobNotVerifiedException;
import dev.hrutvik.filesync.verification.BlobVerificationStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileVersionService {
 private final FileVersionStore store;
 private final BlobVerificationStore verification;
 private final TenantQuotaStore quota;

 public FileVersionService(FileVersionStore store, BlobVerificationStore verification, TenantQuotaStore quota) {
  this.store = store;
  this.verification = verification;
  this.quota = quota;
 }

 @Transactional
 public CommitVersionResponse commit(String tenant, CommitVersionRequest request) {
  String path = normalize(request.path());
  String hash = request.contentHash().toLowerCase(Locale.ROOT);
  ConflictPolicy policy = request.effectiveConflictPolicy();
  String fingerprint = policy == ConflictPolicy.REJECT
    ? fingerprint("PUT", request.operationId(), request.deviceId(), path, hash, request.sizeBytes(), request.baseVersion())
    : fingerprint("PUT", request.operationId(), request.deviceId(), path, hash, request.sizeBytes(), request.baseVersion(), policy);
  var replay = store.replay(tenant, request.operationId(), fingerprint);
  if (replay.isPresent()) return replay.get();
  if (!verification.isVerified(hash)) throw new BlobNotVerifiedException("content hash has not passed server-side verification");

  var head = store.lockHead(tenant, path);
  long nextVersion;
  UUID fileId;
  String targetPath = path;
  Long targetBase = request.baseVersion();
  long quotaDelta;
  if (head.isEmpty()) {
   if (request.baseVersion() != null) throw conflict("file does not exist", null, request.baseVersion());
   nextVersion = 1;
   fileId = UUID.randomUUID();
   quotaDelta = request.sizeBytes();
  } else {
   long current = head.get().version();
   if (request.baseVersion() == null || request.baseVersion() != current) {
    if (policy != ConflictPolicy.KEEP_BOTH) throw conflict("stale base version", current, request.baseVersion());
    targetPath = conflictPath(path, request.deviceId(), request.operationId());
    if (store.lockHead(tenant, targetPath).isPresent()) throw conflict("conflict-copy path already exists", null, request.baseVersion());
    nextVersion = 1;
    fileId = UUID.randomUUID();
    targetBase = null;
    quotaDelta = request.sizeBytes();
   } else {
    nextVersion = current + 1;
    fileId = head.get().fileId();
    quotaDelta = request.sizeBytes() - (head.get().deleted() ? 0 : head.get().sizeBytes());
   }
  }
  quota.applyDelta(tenant, quotaDelta);
  var persisted = new CommitVersionRequest(request.operationId(), request.deviceId(), targetPath, hash, request.sizeBytes(), targetBase, policy);
  return store.save(tenant, fileId, nextVersion, persisted, fingerprint);
 }

 @Transactional
 public CommitVersionResponse delete(String tenant, String rawPath, DeleteFileRequest request) {
  String path = normalize(rawPath.replaceFirst("^/", ""));
  String fingerprint = fingerprint("DELETE", request.operationId(), request.deviceId(), path, request.baseVersion());
  var replay = store.replay(tenant, request.operationId(), fingerprint);
  if (replay.isPresent()) return replay.get();
  var head = store.lockHead(tenant, path).orElseThrow(() -> conflict("file does not exist", null, request.baseVersion()));
  if (head.deleted()) throw conflict("file is already deleted", head.version(), request.baseVersion());
  if (request.baseVersion() == null || request.baseVersion() != head.version()) throw conflict("stale base version", head.version(), request.baseVersion());
  quota.applyDelta(tenant, -head.sizeBytes());
  return store.tombstone(tenant, head, path, request, fingerprint);
 }

 static String normalize(String path) {
  String normalized = path.trim().replace('\\', '/').replaceAll("/+", "/");
  if (normalized.startsWith("/") || normalized.equals("..") || normalized.startsWith("../") || normalized.contains("/../") || normalized.endsWith("/.."))
   throw new IllegalArgumentException("path must be relative and cannot traverse parents");
  return normalized;
 }

 static String conflictPath(String path, UUID deviceId, String operationId) {
  String token = fingerprint(deviceId, operationId).substring(0, 12);
  String suffix = " (conflict-" + deviceId.toString().substring(0, 8) + "-" + token + ")";
  int slash = path.lastIndexOf('/');
  String directory = slash < 0 ? "" : path.substring(0, slash + 1);
  String name = slash < 0 ? path : path.substring(slash + 1);
  int dot = name.lastIndexOf('.');
  String stem = dot > 0 ? name.substring(0, dot) : name;
  String extension = dot > 0 ? name.substring(dot) : "";
  int maxStem = 1024 - directory.length() - suffix.length() - extension.length();
  if (maxStem < 1) throw new IllegalArgumentException("path is too long to create a conflict copy");
  return directory + stem.substring(0, Math.min(stem.length(), maxStem)) + suffix + extension;
 }

 private static VersionConflictException conflict(String reason, Long current, Long supplied) {
  return new VersionConflictException(reason + "; currentVersion=" + current + ", baseVersion=" + supplied);
 }

 public static String fingerprint(Object... values) {
  try {
   var digest = MessageDigest.getInstance("SHA-256");
   return HexFormat.of().formatHex(digest.digest(Arrays.toString(values).getBytes(StandardCharsets.UTF_8)));
  } catch (NoSuchAlgorithmException e) {
   throw new IllegalStateException(e);
  }
 }
}
