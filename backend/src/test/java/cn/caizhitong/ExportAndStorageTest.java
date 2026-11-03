package cn.caizhitong;
import cn.caizhitong.service.*;
import cn.caizhitong.mapper.OperationsMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.ss.usermodel.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExportAndStorageTest {
 @TempDir Path temp;
 @Test void localStorageRejectsTraversalAndKeepsBytes()throws Exception{
  var storage=new LocalAttachmentStorage(temp.toString());String key=UUID.randomUUID()+".png";storage.save(key,new byte[]{1,2,3});assertThat(storage.read(key)).containsExactly(1,2,3);assertThat(storage.exists(key)).isTrue();
  for(String invalid:List.of("../../secret.png","C:/secret.png","../"+key,"fake.png"))assertThatThrownBy(()->storage.read(invalid)).isInstanceOf(BusinessException.class);storage.delete(key);assertThat(storage.exists(key)).isFalse();
 }
 @Test void storageFailureReturnsExplicitErrorAndNeverInsertsCompletedMetadata()throws Exception{
  var db=mock(OperationsMapper.class);var storage=mock(AttachmentStorage.class);doThrow(new IOException("disk full")).when(storage).save(anyString(),any());var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
  assertThatThrownBy(()->new AttachmentService(db,storage).upload(new MockMultipartFile("file","a.png","image/png",out.toByteArray()),null)).isInstanceOf(BusinessException.class).hasMessageContaining("照片保存失败");verifyNoInteractions(db);verify(storage).delete(anyString());
 }
 Map<String,Object> meta(String type){return Map.of("type",type,"title","学期耗材汇总表","from","2026-09-01","to","2027-01-31","businessZone","Asia/Shanghai","generatedAt","2026-11-03T08:00:00Z","notes",List.of("按历史单价统计；币种分别汇总。"));}
 @Test void workbookPreservesLargeDecimalsAndTreatsFormulaLikeTextAsText()throws Exception{
  var row=new LinkedHashMap<String,Object>();row.put("material_code","=HYPERLINK(\"bad\")");row.put("material_name","中文材料");row.put("net_amount",new BigDecimal("1234567890123456.1234567"));var b=new ReportService.Bundle(meta("semester"),List.of(row),List.of(),1);var exporter=new ReportExporter();
  try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(exporter.excel(b)))){var sheet=workbook.getSheetAt(0);assertThat(sheet.getRow(4).getCell(0).getCellType()).isEqualTo(CellType.STRING);assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).startsWith("=HYPERLINK");int column=exporter.columns("semester").stream().map(ReportExporter.Column::key).toList().indexOf("net_amount");var cell=sheet.getRow(4).getCell(column);assertThat(cell.getCellType()).isEqualTo(CellType.STRING);assertThat(cell.getStringCellValue()).isEqualTo("1234567890123456.1234567");}
 }
 @Test void longChinesePdfPaginatesWithRepeatedHeaderAndFinalContent()throws Exception{
  var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<35;i++)rows.add(Map.of("material_code","MAT-"+i,"material_name","长中文材料说明".repeat(20),"spec","规格参数需要逐行完整显示。".repeat(40),"net_amount",new BigDecimal("1.851")));rows.add(Map.of("material_name","最终记录"));var b=new ReportService.Bundle(meta("semester"),rows,List.of(),rows.size());
  try(var pdf=Loader.loadPDF(new ReportExporter().pdf(b))){assertThat(pdf.getNumberOfPages()).isGreaterThan(2);String text=new PDFTextStripper().getText(pdf).replaceAll("\\s+","");assertThat(text).contains("最终记录","规格参数需要逐行完整显示。");assertThat(text.split("材料编码",-1).length-1).isEqualTo(pdf.getNumberOfPages());}
 }
 @Test void actualJavaMailSendsOnlyToControlledLoopbackSmtp()throws Exception{
  try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
   var executor=Executors.newSingleThreadExecutor();try{
    var received=executor.submit(()->{try(var socket=server.accept()){socket.setSoTimeout(20000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII),true);writer.println("220 localhost test SMTP");String line;var body=new StringBuilder();boolean data=false;while((line=reader.readLine())!=null){if(data){if(line.equals(".")){data=false;writer.println("250 queued");}else body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");}else if(line.startsWith("EHLO")||line.startsWith("HELO"))writer.println("250 localhost");else if(line.startsWith("MAIL")||line.startsWith("RCPT")||line.startsWith("RSET"))writer.println("250 OK");else if(line.equals("DATA")){data=true;writer.println("354 send data");}else if(line.equals("QUIT")){writer.println("221 bye");break;}else writer.println("250 OK");}return body.toString();}});
    var settings=new MailSettings(server.getInetAddress().getHostAddress(),server.getLocalPort(),"","","lab@example.test","buyer@example.test",false,false,false);new SmtpMailGateway(settings).send("材智通低库存提醒","材料：精密电阻\n当前库存：99\n安全库存：100\nhttp://example.test/alerts",List.of("buyer@example.test"));String raw=received.get(10,TimeUnit.SECONDS);var message=new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.US_ASCII)));assertThat(message.getSubject()).isEqualTo("材智通低库存提醒");assertThat(message.getContent().toString()).contains("精密电阻","99","100","/alerts");
   }finally{executor.shutdownNow();}
  }
 }
}
