package cn.caizhitong.dto;
import java.util.List;
public record InboundDetail(InboundRow order,List<BatchRow> lines,List<StockSummary.Money> totals) {}

