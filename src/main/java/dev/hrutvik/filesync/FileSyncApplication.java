package dev.hrutvik.filesync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling public class FileSyncApplication { public static void main(String[] args){SpringApplication.run(FileSyncApplication.class,args);} }
