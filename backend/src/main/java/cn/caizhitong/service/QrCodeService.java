package cn.caizhitong.service;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Map;
@Service
public class QrCodeService {
 private final String origin;
 public QrCodeService(@Value("${app.public-web-url:}") String value) {
  origin=value.replaceAll("/+$","");
  if(!origin.isBlank()) {
   URI uri=URI.create(origin);
   if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()))
    throw new IllegalArgumentException("PUBLIC_WEB_URL must be an HTTP(S) origin, without credentials, path, query or fragment");
  }
 }
 public String content(String code) {
  if(origin.isBlank()) return null;
  return origin+"/stock/batches/"+code;
 }
 public byte[] png(String code) {
  if(origin.isBlank()) throw new BusinessException(503,"请配置 PUBLIC_WEB_URL 后生成批次标签");
  try {
   var matrix=new QRCodeWriter().encode(content(code),BarcodeFormat.QR_CODE,320,320,Map.of(EncodeHintType.MARGIN,2,EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M));
   var out=new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(matrix,"PNG",out); return out.toByteArray();
  } catch(BusinessException e){throw e;}
  catch(Exception e){throw new IllegalStateException("Cannot encode batch QR",e);}
 }
}

