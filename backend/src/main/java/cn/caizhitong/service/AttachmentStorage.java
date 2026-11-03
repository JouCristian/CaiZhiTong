package cn.caizhitong.service;
import java.io.IOException;
public interface AttachmentStorage {
 void save(String key,byte[] bytes)throws IOException;
 byte[] read(String key)throws IOException;
 boolean exists(String key);
 void delete(String key)throws IOException;
}
