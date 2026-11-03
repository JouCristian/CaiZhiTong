# 实际运行凭证样本

sprint04-issue-example.pdf 由后端 IssuePdfService 为独立测试数据库的 test_student 实际生成，包含真实持久化的测试单号、材料、批次、精确金额和手写签字栏。它不是日常用户凭证，不含密码或 JWT。

生成、解析、A4 / 字体嵌入与价格快照验证位于 IssueIntegrationTest；对应渲染见 ../screenshots/sprint04-pdf.png。常规界面通过受权限保护的 /api/issues/{id}/pdf 下载当前用户有权查看的凭证。
