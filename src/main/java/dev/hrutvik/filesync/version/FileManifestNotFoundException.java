package dev.hrutvik.filesync.version;

public class FileManifestNotFoundException extends RuntimeException {
 public FileManifestNotFoundException(String path){super("file manifest not found: "+path);}
}
