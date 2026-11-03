package cn.caizhitong.service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
@Service
public class ReportExporter {
 public record Column(String key,String label,int width,boolean number){}
 private Column c(String key,String label,int width){return new Column(key,label,width,key.endsWith("quantity")||key.endsWith("amount")||Set.of("original_quantity","returned_total","unit_price_snapshot").contains(key));}
 public List<Column> columns(String type){
  var result=new ArrayList<Column>();if(!type.equals("semester")){result.add(c("issue_no","领用单号",132));result.add(c("recipient_name","领用人",70));result.add(c("project_no","项目号",100));result.add(c("course","课程",90));result.add(c("usage","用途",110));}
  result.addAll(List.of(c("material_code","材料编码",132),c("material_name","材料名称",110),c("spec","规格型号",130),c("unit","单位",40),c("currency","币种",45)));
  if(type.equals("semester"))result.add(c("category_path","所属类别",90));else{result.add(c("batch_code","批次号",132));result.add(c("unit_price_snapshot","单价快照",65));result.add(c("original_quantity","原领用量",65));result.add(c("returned_total","累计已归还",65));}
  result.addAll(List.of(c("issue_quantity","本期领用量",65),c("return_quantity","本期归还量",65),c("net_quantity","净消耗量",65),c("issue_amount","领用金额",75),c("return_amount","归还冲减金额",75),c("net_amount","净消耗金额",75)));
  if(type.equals("semester"))result.addAll(List.of(c("scrap_quantity","仓库报废量",65),c("scrap_amount","仓库报废金额",75)));
  else{result.add(c("issued_at","原领用时间",160));result.add(c("last_event_at","最近业务时间",160));}
  return result;
 }
 public String display(Object value,String zone){if(value==null)return "";if(value instanceof BigDecimal n)return n.stripTrailingZeros().toPlainString();if(value instanceof Timestamp t)return t.toInstant().atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));return value.toString();}
 private List<Map<String,Object>> all(ReportService.Bundle bundle){var rows=new ArrayList<>(bundle.rows());for(var total:bundle.totals()){var summary=new LinkedHashMap<>(total);summary.put("material_name","合计（"+total.get("currency")+" / "+total.get("unit")+"）");summary.put("material_code","合计");summary.put("issue_no","合计");summary.put("_total",true);rows.add(summary);}return rows;}
 public byte[] excel(ReportService.Bundle bundle){
  var columns=columns(bundle.meta().get("type").toString());String zone=bundle.meta().get("businessZone").toString();
  try(var workbook=new SXSSFWorkbook(100);var out=new ByteArrayOutputStream()){
   try{
    var sheet=workbook.createSheet(bundle.meta().get("title").toString());sheet.setDisplayGridlines(false);
    var bodyFont=workbook.createFont();bodyFont.setFontName("Microsoft YaHei");bodyFont.setFontHeightInPoints((short)10);
    var titleFont=workbook.createFont();titleFont.setFontName("Microsoft YaHei");titleFont.setFontHeightInPoints((short)16);titleFont.setBold(true);
    var title=workbook.createCellStyle();title.setFont(titleFont);var body=workbook.createCellStyle();body.setFont(bodyFont);body.setVerticalAlignment(VerticalAlignment.TOP);body.setWrapText(true);
    var header=workbook.createCellStyle();header.cloneStyleFrom(body);header.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setAlignment(HorizontalAlignment.CENTER);header.setBorderBottom(BorderStyle.THIN);
    var numeric=workbook.createCellStyle();numeric.cloneStyleFrom(body);numeric.setAlignment(HorizontalAlignment.RIGHT);numeric.setDataFormat(workbook.createDataFormat().getFormat("0.#######;[Red]-0.#######;0"));
    var totalStyle=workbook.createCellStyle();totalStyle.cloneStyleFrom(body);totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    var totalNumeric=workbook.createCellStyle();totalNumeric.cloneStyleFrom(numeric);totalNumeric.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());totalNumeric.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    sheet.createRow(0).setHeightInPoints(12);var titleRow=sheet.createRow(1);titleRow.setHeightInPoints(28);var t=titleRow.createCell(0);t.setCellValue("材智通 · "+bundle.meta().get("title"));t.setCellStyle(title);sheet.addMergedRegion(new CellRangeAddress(1,1,0,columns.size()-1));
    var range=sheet.createRow(2);range.setHeightInPoints(24);var rangeCell=range.createCell(0);rangeCell.setCellValue("统计范围："+bundle.meta().get("from")+" 至 "+bundle.meta().get("to")+" · "+zone);rangeCell.setCellStyle(body);sheet.addMergedRegion(new CellRangeAddress(2,2,0,columns.size()-1));
    var h=sheet.createRow(3);h.setHeightInPoints(32);for(int i=0;i<columns.size();i++){var cell=h.createCell(i);cell.setCellValue(columns.get(i).label());cell.setCellStyle(header);sheet.setColumnWidth(i,Math.min(55,Math.max(12,columns.get(i).width()/5))*256);}sheet.createFreezePane(1,4);sheet.setRepeatingRows(new CellRangeAddress(3,3,-1,-1));
    int index=4;for(var row:all(bundle)){var r=sheet.createRow(index++);r.setHeightInPoints(36);boolean isTotal=Boolean.TRUE.equals(row.get("_total"));for(int i=0;i<columns.size();i++){var col=columns.get(i);var cell=r.createCell(i);Object value=row.get(col.key());cell.setCellStyle(col.number()?(isTotal?totalNumeric:numeric):(isTotal?totalStyle:body));if(value instanceof BigDecimal n&&n.precision()<=15){cell.setCellValue(n.doubleValue());}else cell.setCellValue(display(value,zone));}}
    if(bundle.rows().isEmpty()){var row=sheet.createRow(index++);row.createCell(0).setCellValue("无符合条件的记录");sheet.addMergedRegion(new CellRangeAddress(index-1,index-1,0,columns.size()-1));}
    var notes=workbook.createSheet("统计口径");int n=0;for(Object text:(List<?>)bundle.meta().get("notes")){notes.createRow(n++).createCell(0).setCellValue(text.toString());}notes.createRow(n++).createCell(0).setCellValue("超过 Excel 15 位有效数字的精确数值以文本保存；所有合计由后端 BigDecimal 计算。");notes.createRow(n).createCell(0).setCellValue("数据来源：已完成领用、完好归还和仓库报废。生成时间："+bundle.meta().get("generatedAt"));notes.setColumnWidth(0,110*256);sheet.setAutoFilter(new CellRangeAddress(3,Math.max(3,3+bundle.rows().size()),0,columns.size()-1));
    workbook.write(out);return out.toByteArray();
   }finally{workbook.dispose();}
  }catch(IOException e){throw new IllegalStateException("Cannot export workbook",e);}
 }
 public byte[] pdf(ReportService.Bundle bundle){
  try(var doc=new PDDocument();var input=new ClassPathResource("fonts/CaiZhiTongSansSC-Regular.ttf").getInputStream();var result=new ByteArrayOutputStream()){
   var font=PDType0Font.load(doc,input,true);var table=new PdfTable(doc,font,bundle,columns(bundle.meta().get("type").toString()));for(var row:all(bundle))table.row(row);if(bundle.rows().isEmpty())table.empty();table.close();
   for(int i=0;i<doc.getNumberOfPages();i++)try(var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true)){footer.beginText();footer.setFont(font,8);footer.newLineAtOffset(35,22);footer.showText("CaiZhiTong · "+(i+1)+" / "+doc.getNumberOfPages());footer.endText();}
   doc.save(result);return result.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Cannot export report PDF",e);}
 }
 private final class PdfTable implements AutoCloseable {
  final PDDocument doc;final PDType0Font font;final ReportService.Bundle bundle;final List<Column> columns;final float[] widths;PDPageContentStream out;float y;final float width=PDRectangle.A3.getHeight()-70;final float lineHeight=12;
  PdfTable(PDDocument doc,PDType0Font font,ReportService.Bundle bundle,List<Column> columns)throws IOException{this.doc=doc;this.font=font;this.bundle=bundle;this.columns=columns;this.widths=new float[columns.size()];int total=columns.stream().mapToInt(Column::width).sum();for(int i=0;i<columns.size();i++)widths[i]=width*columns.get(i).width()/total;page();}
  String safe(String s){var b=new StringBuilder();s.codePoints().forEach(cp->{if(Character.isISOControl(cp)){b.append(' ');return;}String c=new String(Character.toChars(cp));try{font.getStringWidth(c);b.append(c);}catch(Exception e){b.append('?');}});return b.toString();}
  List<String> wrap(String value,float width)throws IOException{var lines=new ArrayList<String>();var b=new StringBuilder();float w=0;for(int cp:safe(value).codePoints().toArray()){String c=new String(Character.toChars(cp));float n=font.getStringWidth(c)*8/1000;if(w+n>width-8&&b.length()>0){lines.add(b.toString());b.setLength(0);w=0;}b.append(c);w+=n;}lines.add(b.toString());return lines;}
  void text(String s,float x,float y,int size)throws IOException{out.beginText();out.setFont(font,size);out.setNonStrokingColor(new Color(35,44,58));out.newLineAtOffset(x,y);out.showText(safe(s));out.endText();}
  void page()throws IOException{if(out!=null)out.close();var page=new PDPage(new PDRectangle(PDRectangle.A3.getHeight(),PDRectangle.A3.getWidth()));doc.addPage(page);out=new PDPageContentStream(doc,page);y=PDRectangle.A3.getWidth()-38;text("材智通 · "+bundle.meta().get("title"),35,y,17);y-=24;text("统计范围："+bundle.meta().get("from")+" 至 "+bundle.meta().get("to")+"    "+bundle.meta().get("businessZone"),35,y,10);for(Object note:(List<?>)bundle.meta().get("notes")){y-=14;text(note.toString(),35,y,8);}y-=15;var headers=new ArrayList<List<String>>();for(int i=0;i<columns.size();i++)headers.add(wrap(columns.get(i).label(),widths[i]));int lines=headers.stream().mapToInt(List::size).max().orElse(1);draw(headers,0,lines,true);}
  void draw(List<List<String>> values,int offset,int count,boolean filled)throws IOException{float height=count*lineHeight+10,x=35;for(int i=0;i<columns.size();i++){if(filled){out.setNonStrokingColor(new Color(235,241,249));out.addRect(x,y-height,widths[i],height);out.fill();}out.setStrokingColor(new Color(211,218,227));out.setLineWidth(.35f);out.addRect(x,y-height,widths[i],height);out.stroke();for(int k=0;k<count;k++)if(offset+k<values.get(i).size())text(values.get(i).get(offset+k),x+4,y-12-k*lineHeight,8);x+=widths[i];}y-=height;}
  void row(Map<String,Object> row)throws IOException{var lines=new ArrayList<List<String>>();for(int i=0;i<columns.size();i++)lines.add(wrap(display(row.get(columns.get(i).key()),bundle.meta().get("businessZone").toString()),widths[i]));int max=lines.stream().mapToInt(List::size).max().orElse(1),offset=0;while(offset<max){int available=(int)((y-50-10)/lineHeight);if(available<1){page();continue;}int count=Math.min(max-offset,available);draw(lines,offset,count,Boolean.TRUE.equals(row.get("_total")));offset+=count;if(offset<max)page();}}
  void empty()throws IOException{if(y<70)page();text("无符合条件的记录",40,y-25,11);y-=45;}
  public void close()throws IOException{out.close();}
 }
}
