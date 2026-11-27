package dev.hrutvik.filesync.version;

import java.util.Optional;

public interface FileManifestCache {
 Optional<FileManifestResponse> get(String tenant,String path);
 void put(String tenant,String path,FileManifestResponse manifest);
 void evictAfterCommit(String tenant,String... paths);

 FileManifestCache NOOP = new FileManifestCache(){
  public Optional<FileManifestResponse> get(String tenant,String path){return Optional.empty();}
  public void put(String tenant,String path,FileManifestResponse manifest){}
  public void evictAfterCommit(String tenant,String... paths){}
 };
}
