# 中文 PDF 字体

CaiZhiTongSansSC-Regular.ttf 基于 Google Noto CJK 的 NotoSansSC-VF.ttf（wght=400）生成静态 TrueType，服务端随 JAR 分发并嵌入 PDF 子集，不依赖 Windows 字体或外部字体服务。

来源：https://github.com/notofonts/noto-cjk/tree/main/Sans/Variable/TTF/Subset

授权见同目录 OFL.txt（SIL Open Font License 1.1）。罕见未覆盖字符以问号回退，常用简体中文、拉丁字母、数字均内嵌。

移除与标准汉字共用字形的康熙部首 cmap 别名，避免 PDFBox 反向映射把普通汉字提取为兼容部首；派生字体族更名 CaiZhiTong Sans SC。保留原版权和 OFL 授权。

