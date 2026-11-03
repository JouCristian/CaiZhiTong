package cn.caizhitong.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.nio.file.*;
import java.io.IOException;
@Component
@ConditionalOnProperty(name="app.storage.mode",havingValue="local",matchIfMissing=true)
public class LocalAttachmentStorage implements AttachmentStorage {
 private final Path root;
 public LocalAttachmentStorage(@Value("${app.storage.root:./.runtime/uploads}")String root){this.root=Path.of(root).toAbsolutePath().normalize();}
 private Path path(String key){if(!key.matches("[0-9a-f-]{36}\\.(png|jpg|webp)"))throw new BusinessException(400,"附件标识不合法");Path p=root.resolve(key).normalize();if(!p.startsWith(root)||Files.isSymbolicLink(p))throw new BusinessException(400,"附件路径不合法");return p;}
 public void save(String key,byte[] bytes)throws IOException{Files.createDirectories(root);Path target=path(key),temp=Files.createTempFile(root,"upload-",".tmp");try{Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
 public byte[] read(String key)throws IOException{return Files.readAllBytes(path(key));}
 public boolean exists(String key){return Files.isRegularFile(path(key),LinkOption.NOFOLLOW_LINKS);}
 public void delete(String key)throws IOException{Files.deleteIfExists(path(key));}
}
