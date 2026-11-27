package dev.hrutvik.filesync.version;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileManifestService {
 private final FileVersionStore store; private final FileManifestCache cache;
 public FileManifestService(FileVersionStore store,FileManifestCache cache){this.store=store;this.cache=cache;}
 @Transactional(readOnly=true) public FileManifestResponse get(String tenant,String rawPath,boolean strong){String path=FileVersionService.normalize(rawPath.replaceFirst("^/",""));if(!strong){var cached=cache.get(tenant,path);if(cached.isPresent())return cached.get();}var head=store.findHead(tenant,path).filter(candidate->!candidate.deleted()).orElseThrow(()->new FileManifestNotFoundException(path));var response=new FileManifestResponse(head.fileId(),head.version(),path,head.contentHash(),head.sizeBytes(),head.committedAt(),false);cache.put(tenant,path,response);return response;}
}
