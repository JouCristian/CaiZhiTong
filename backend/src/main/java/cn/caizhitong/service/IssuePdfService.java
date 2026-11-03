package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service
public class IssuePdfService {
 public byte[] generate(IssueDetail detail) {
  try(var doc=new PDDocument();var stream=new ClassPathResource("fonts/CaiZhiTongSansSC-Regular.ttf").getInputStream()){
   var font=PDType0Font.load(doc,stream,true);
   var layout=new Layout(doc,font,detail.order().issueNo);
   layout.heading("材智通  CaiZhiTong",20);
   layout.heading("材料领用凭证",15);
   layout.text("领用单号："+detail.order().issueNo,11);
   layout.text("领用人："+detail.order().recipientName+"（"+detail.order().accountName+"）",11);
   layout.text("领用日期："+detail.order().createdAt.atZoneSameInstant(ZoneId.of(detail.order().businessZone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))+"  "+detail.order().businessZone,10);
   layout.text("项目编号："+detail.order().projectNo,11);
   layout.text("课程："+(detail.order().course==null?"未关联课程":detail.order().course),11);
   layout.text("用途："+detail.order().usage,11);
   layout.rule();
   for(var line:detail.lines()){
    layout.ensure(128);
    layout.heading("明细 "+line.lineNo+"  "+line.materialName,12);
    layout.text("材料编码："+line.materialCode+"    批次："+line.batchCode,10);
    if(line.spec!=null)layout.text("规格："+line.spec,10);
    layout.text("数量："+line.quantity.stripTrailingZeros().toPlainString()+" "+line.unit+"    单价："+detail.order().currency+" "+line.unitPriceSnapshot.toPlainString(),11);
    layout.text("金额："+detail.order().currency+" "+line.amount.stripTrailingZeros().toPlainString(),11);
    layout.text("关联流水："+line.transactionNo,9);
    layout.rule();
   }
   layout.ensure(150);
   layout.heading("合计："+detail.order().currency+" "+detail.order().totalAmount.stripTrailingZeros().toPlainString(),14);
   if(detail.order().remark!=null)layout.text("备注："+detail.order().remark,10);
   layout.ensure(115);layout.gap(24);
   layout.text("领用人签字：________________        经办人签字：________________",11);
   layout.gap(14);layout.text("日期：______________________        日期：______________________",10);
   layout.gap(16);layout.text("本凭证由系统根据已完成的领用记录生成，签字栏供线下手写确认。",9);
   layout.close();
   for(int i=0;i<doc.getNumberOfPages();i++){
    try(var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true)){
     footer.beginText();footer.setFont(font,9);footer.setNonStrokingColor(Color.GRAY);footer.newLineAtOffset(46,28);footer.showText(detail.order().issueNo+"    "+(i+1)+" / "+doc.getNumberOfPages());footer.endText();
    }
   }
   var out=new ByteArrayOutputStream();doc.save(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Cannot generate issue PDF",e);}
 }
 private static final class Layout implements AutoCloseable {
  final PDDocument doc;final PDType0Font font;final String reference;PDPageContentStream out;float y;
  Layout(PDDocument doc,PDType0Font font,String reference)throws IOException{this.doc=doc;this.font=font;this.reference=reference;page();}
  void page()throws IOException{if(out!=null)out.close();var p=new PDPage(PDRectangle.A4);doc.addPage(p);out=new PDPageContentStream(doc,p);y=790;}
  void ensure(float height)throws IOException{if(y-height<65)page();}
  void gap(float size)throws IOException{ensure(size);y-=size;}
  void heading(String s,int size)throws IOException{gap(8);text(s,size);gap(5);}
  void rule()throws IOException{ensure(16);out.setStrokingColor(new Color(215,221,230));out.moveTo(46,y-3);out.lineTo(549,y-3);out.stroke();y-=18;}
  String printable(String s){StringBuilder result=new StringBuilder();s.codePoints().forEach(cp->{if(cp=='\n'){result.append('\n');return;}if(Character.isISOControl(cp)){result.append(' ');return;}try{String c=new String(Character.toChars(cp));font.getStringWidth(c);result.append(c);}catch(Exception e){result.append('?');}});return result.toString();}
  void text(String value,int size)throws IOException{
   for(String paragraph:printable(value).split("\\R",-1)){
    var line=new StringBuilder();float width=0;
    for(int cp:paragraph.codePoints().toArray()){
     String c=new String(Character.toChars(cp));float w=font.getStringWidth(c)/1000*size;
     if(width+w>503&&line.length()>0){draw(line.toString(),size);line.setLength(0);width=0;}
     line.append(c);width+=w;
    }
    draw(line.toString(),size);
   }
  }
  void draw(String text,int size)throws IOException{ensure(size*1.6f);out.beginText();out.setFont(font,size);out.setNonStrokingColor(new Color(32,43,58));out.newLineAtOffset(46,y);out.showText(text);out.endText();y-=size*1.6f;}
  public void close()throws IOException{out.close();}
 }
}


