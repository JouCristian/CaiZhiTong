package cn.caizhitong.dto;
import java.util.List;
public record IssueDetail(IssueOrderRow order,List<IssueLineRow> lines) {}
