package cn.caizhitong.service;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class AttachmentService {
 private final OperationsMapper db;private final AttachmentStorage storage;
 public AttachmentService(OperationsMapper db,AttachmentStorage storage){this.db=db;this.storage=storage;}
 public Map<String,Object> upload(MultipartFile file,UserPrincipal user){
  if(file.isEmpty()||file.getSize()>5242880)throw new BusinessException(400,"照片不能为空且每张不超过 5 MB");
  String key=null;
  try{
   byte[] bytes=file.getBytes();String ext;
   try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new BusinessException(400,"文件不是有效的 JPG、PNG 或 WebP 图片");var reader=readers.next();
    try{reader.setInput(stream);String type=reader.getFormatName().toLowerCase(Locale.ROOT);ext=switch(type){case"jpeg","jpg"->"jpg";case"png"->"png";case"webp"->"webp";default->throw new BusinessException(400,"仅支持 JPG、PNG、WebP");};long w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w*h>20000000)throw new BusinessException(400,"图片尺寸过大，最多 2000 万像素");if(reader.read(0)==null)throw new BusinessException(400,"图片数据损坏");}finally{reader.dispose();}
   }catch(javax.imageio.IIOException e){throw new BusinessException(400,"图片数据损坏，请重新选择照片");}
   key=UUID.randomUUID()+"."+ext;storage.save(key,bytes);String mime=ext.equals("jpg")?"image/jpeg":"image/"+ext;
   var row=db.one("INSERT INTO stock_attachment(storage_key,media_type,byte_size,sha256,uploaded_by) VALUES(?,?,?,?,?) RETURNING id,media_type,byte_size,created_at",key,mime,bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),user.user().getId());return api(row);
  }catch(BusinessException e){throw e;}catch(Exception e){if(key!=null)try{storage.delete(key);}catch(Exception ignored){}throw new BusinessException(500,"照片保存失败，尚未生成报废记录，请重试或移除照片");}
 }
 @Transactional(readOnly=true)public Map<String,Object> metadata(Long id,UserPrincipal user){
  var row=db.one("SELECT a.*,s.id AS scrap_id FROM stock_attachment a LEFT JOIN scrap_order s ON s.attachment_id=a.id WHERE a.id=?",id);if(row==null)throw new BusinessException(404,"附件不存在");
  boolean manager=StockLifecycleService.manages(user);boolean inventory=user.roles().stream().anyMatch(r->r.equals("TEACHER")||r.equals("PURCHASER"));
  if(!manager&&!(row.get("scrap_id")!=null&&inventory))throw new BusinessException(403,"无权访问该损耗照片");return row;
 }
 public byte[] read(Map<String,Object> row){try{return storage.read(text(row,"storage_key"));}catch(IOException e){throw new BusinessException(404,"附件文件不可用，请联系管理员");}}
 public void validateForScrap(Long id,UserPrincipal user){var row=db.one("SELECT * FROM stock_attachment WHERE id=? FOR UPDATE",id);if(row==null)throw new BusinessException(404,"请先成功上传照片");if(id(row,"uploaded_by")!=user.user().getId()&&!user.roles().contains("ADMIN"))throw new BusinessException(403,"不能使用他人未关联的照片");if(db.count("SELECT count(*) FROM scrap_order WHERE attachment_id=?",id)>0)throw new BusinessException(409,"照片已关联其他报废记录");if(!storage.exists(text(row,"storage_key")))throw new BusinessException(409,"照片尚未可靠保存，请重新上传");}
}
