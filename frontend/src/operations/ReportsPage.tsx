import { useEffect, useState } from "react";
import {
  Alert,
  App,
  Button,
  DatePicker,
  Empty,
  Form,
  Grid,
  Input,
  InputNumber,
  Select,
  Skeleton,
  Space,
  Table,
  Tabs,
  TreeSelect,
} from "antd";
import { FileExcelOutlined, FilePdfOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { requestBlob } from "../api";
import { useAuth } from "../auth";
import type { Category } from "../materials/types";
import { categoryOptions, quantity } from "../materials/types";
import { query } from "../stock/types";
import { LoadError, useRemote } from "./shared";
import "./operations.css";
type Cell = string | number | null;
type Row = Record<string, Cell>;
interface Report {
  meta: {
    title: string;
    from: string;
    to: string;
    academicYear: number;
    term: string;
    businessZone: string;
    fileStem: string;
    notes: string[];
  };
  records: Row[];
  totals: Row[];
  total: number;
  page: number;
  size: number;
}
interface Filters {
  academicYear?: number;
  term?: string;
  from?: string;
  to?: string;
  categoryId?: number;
  keyword?: string;
  course?: string;
  projectNo?: string;
  userId?: number;
}
type Values = Filters & { dates?: [dayjs.Dayjs, dayjs.Dayjs] };
const names = {
  semester: "学期耗材",
  funding: "经费对应",
  personal: "个人领用",
};
const common: [string, string][] = [
  ["materialCode", "材料编码"],
  ["materialName", "材料名称"],
  ["spec", "规格型号"],
  ["unit", "单位"],
  ["currency", "币种"],
];
function columns(type: string): [string, string][] {
  return [
    ...(type === "semester"
      ? []
      : ([
          ["issueNo", "领用单号"],
          ["recipientName", "领用人"],
          ["projectNo", "项目号"],
          ["course", "课程"],
          ["usage", "用途"],
        ] as [string, string][])),
    ...common,
    ...((type === "semester"
      ? [["categoryPath", "类别"]]
      : [
          ["batchCode", "批次"],
          ["unitPriceSnapshot", "单价快照"],
          ["originalQuantity", "原领用量"],
          ["returnedTotal", "累计归还量"],
        ]) as [string, string][]),
    ["issueQuantity", "本期领用量"],
    ["returnQuantity", "本期归还量"],
    ["netQuantity", "净消耗量"],
    ["issueAmount", "领用金额"],
    ["returnAmount", "归还冲减金额"],
    ["netAmount", "净消耗金额"],
    ...((type === "semester"
      ? [
          ["scrapQuantity", "仓库报废量"],
          ["scrapAmount", "仓库报废金额"],
        ]
      : [
          ["issuedAt", "原领用时间"],
          ["lastEventAt", "最近业务时间"],
        ]) as [string, string][]),
  ];
}
function display(key: string, value: Cell | undefined, zone = "Asia/Shanghai") {
  if (value == null) return "—";
  if (key.endsWith("At")) return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short", timeZone: zone }).format(new Date(String(value)));
  if (/Quantity$|Amount$|returnedTotal|unitPriceSnapshot/.test(key))
    return quantity(String(value));
  return String(value);
}
export function ReportsPage() {
  const { user } = useAuth(),
    { message } = App.useApp(),
    screens = Grid.useBreakpoint(),
    manager = !!user?.roles.some((r) => ["ADMIN", "LAB_TECH"].includes(r)),
    aggregate =
      manager ||
      !!user?.roles.some((r) => ["TEACHER", "PURCHASER"].includes(r));
  const [type, setType] = useState(aggregate ? "semester" : "personal"),
    [filters, setFilters] = useState<Filters>({}),
    [page, setPage] = useState(1),
    [size, setSize] = useState(20),
    [downloading, setDownloading] = useState(""),
    [userTerm, setUserTerm] = useState(""),
    [form] = Form.useForm<Values>();
  const data = useRemote<Report>(
      "/api/reports/" + type + query({ ...filters, page, size }),
    ),
    categories = useRemote<Category[]>("/api/material-categories/tree"),
    people = useRemote<{ id: number; displayName: string; username: string }[]>(
      "/api/reports/users" + query({ keyword: userTerm }),
      manager,
    );
  const watchedDates = Form.useWatch("dates", form),
    [initialized, setInitialized] = useState(false);
  useEffect(() => {
    if (data.data && !initialized) {
      form.setFieldsValue({
        academicYear: data.data.meta.academicYear,
        term: data.data.meta.term,
      });
      setInitialized(true);
    }
  }, [data.data, form, initialized]);
  function apply(v: Values) {
    const { dates, ...fields } = v;
    setFilters({
      ...fields,
      from: dates?.[0].format("YYYY-MM-DD"),
      to: dates?.[1].format("YYYY-MM-DD"),
    });
    setPage(1);
  }
  async function download(format: string) {
    setDownloading(format);
    try {
      const blob = await requestBlob(
          "/api/reports/" + type + "/export/" + format + query(filters),
          60000,
        ),
        url = URL.createObjectURL(blob),
        a = document.createElement("a");
      a.href = url;
      a.download =
        (data.data?.meta.fileStem || names[type as keyof typeof names]) +
        "." +
        format;
      a.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1500);
    } catch (e) {
      void message.error((e as Error).message);
    } finally {
      setDownloading("");
    }
  }
  const cols = columns(type),
    report = data.data;
  return (
    <div className="page-enter operations-page">
      <div className="page-heading">
        <div>
          <h1>数据报表</h1>
          <p>按真实业务记录统计，保留历史价格与项目归属。</p>
        </div>
        <Space wrap>
          <Button
            aria-label="导出 Excel"
            icon={<FileExcelOutlined />}
            loading={downloading === "xlsx"}
            disabled={!report || data.loading || !!downloading}
            onClick={() => void download("xlsx")}
          >
            导出 Excel
          </Button>
          <Button
            aria-label="导出 PDF"
            icon={<FilePdfOutlined />}
            loading={downloading === "pdf"}
            disabled={!report || data.loading || !!downloading}
            onClick={() => void download("pdf")}
          >
            导出 PDF
          </Button>
        </Space>
      </div>
      <Tabs
        activeKey={type}
        onChange={(v) => {
          setType(v);
          setPage(1);
        }}
        items={Object.entries(names)
          .filter(
            ([k]) =>
              k === "personal" || (k === "funding" ? manager : aggregate),
          )
          .map(([key, label]) => ({ key, label }))}
      />
      <section className="surface report-filter-panel">
        <Form form={form} layout="vertical" onFinish={apply}>
          <div className="report-filter-grid">
            <Form.Item label="学年起始年份" name="academicYear">
              <InputNumber
                aria-label="学年起始年份"
                min={1990}
                max={2200}
                disabled={!!watchedDates}
                style={{ width: "100%" }}
              />
            </Form.Item>
            <Form.Item label="学期" name="term">
              <Select
                aria-label="选择学期"
                disabled={!!watchedDates}
                virtual={false}
                options={[
                  { value: "AUTUMN", label: "秋季（9 月至次年 1 月）" },
                  { value: "SPRING", label: "春季（次年 2 月至 8 月）" },
                ]}
              />
            </Form.Item>
            <Form.Item label="自定义统计范围（优先）" name="dates">
              <DatePicker.RangePicker style={{ width: "100%" }} allowClear />
            </Form.Item>
            <Form.Item label="材料名称或编码" name="keyword">
              <Input allowClear placeholder="搜索材料" maxLength={100} />
            </Form.Item>
            <Form.Item label="材料分类" name="categoryId">
              <TreeSelect
                aria-label="报表分类筛选"
                virtual={false}
                allowClear
                treeData={categoryOptions(categories.data || [])}
                placeholder="全部分类"
                loading={categories.loading}
              />
            </Form.Item>
            <Form.Item label="课程" name="course">
              <Input placeholder="全部课程" allowClear maxLength={100} />
            </Form.Item>
            <Form.Item label="项目号" name="projectNo">
              <Input placeholder="精确项目编号" allowClear maxLength={100} />
            </Form.Item>
            {manager && (
              <Form.Item
                label={
                  type === "personal"
                    ? "领用人（默认本人）"
                    : "领用人（默认全部）"
                }
                name="userId"
              >
                <Select
                  aria-label="报表领用人"
                  virtual={false}
                  showSearch
                  filterOption={false}
                  onSearch={setUserTerm}
                  loading={people.loading}
                  allowClear
                  placeholder={
                    type === "personal" ? user?.displayName : "全部领用人"
                  }
                  options={people.data?.map((u) => ({
                    value: u.id,
                    label: u.displayName + " · " + u.username,
                  }))}
                />
              </Form.Item>
            )}
          </div>
          <div className="report-filter-actions">
            <span>
              {!manager && type === "personal"
                ? "仅统计并导出当前账户的个人记录"
                : "选择条件后点击查询，预览与导出使用相同条件"}
            </span>
            <Space>
              <Button
                onClick={() => {
                  form.resetFields();
                  setFilters({});
                  setPage(1);
                  setInitialized(false);
                }}
              >
                重置条件
              </Button>
              <Button aria-label="查询报表" type="primary" htmlType="submit" loading={data.loading} disabled={data.loading}>
                查询报表
              </Button>
            </Space>
          </div>
        </Form>
        <LoadError error={categories.error} retry={categories.reload} />
        {manager && <LoadError error={people.error} retry={people.reload} />}
      </section>
      <LoadError error={data.error} retry={data.reload} />
      {report && (
        <>
          <div className="report-range">
            <div>
              <h2>{report.meta.title}</h2>
              <p>
                {report.meta.from} 至 {report.meta.to} ·{" "}
                {report.meta.businessZone}
              </p>
            </div>
            <span>共 {report.total} 条统计记录</span>
          </div>
          <section className="surface report-totals" aria-label="报表汇总">
            {report.totals.length ? (
              report.totals.map((r, i) => (
                <div key={i}>
                  <strong>
                    {r.currency} · {r.unit}
                  </strong>
                  <span>
                    领用 {display("issueQuantity", r.issueQuantity)}　归还{" "}
                    {display("returnQuantity", r.returnQuantity)}　净消耗{" "}
                    {display("netQuantity", r.netQuantity)}
                  </span>
                  <b>净消耗金额 {display("netAmount", r.netAmount)}</b>
                  {type === "semester" && (
                    <small>
                      仓库报废 {display("scrapQuantity", r.scrapQuantity)}{" "}
                      {r.unit} / {display("scrapAmount", r.scrapAmount)}{" "}
                      {r.currency}
                    </small>
                  )}
                </div>
              ))
            ) : (
              <span>暂无符合条件的业务数据</span>
            )}
          </section>
        </>
      )}
      <section className="surface operation-panel report-preview">
        {data.loading ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : !report ? (
          <Empty description="调整条件后重新查询" />
        ) : screens.md ? (
          <Table<Row>
            rowKey={(_, i) => String(i)}
            dataSource={report.records}
            loading={data.loading}
            size="small"
            columns={cols.map(([key, title]) => ({
              title,
              dataIndex: key,
              width:
                key.includes("Name") || key === "spec" || key === "usage"
                  ? 180
                  : 130,
              render: (v) => (
                <span className="preserve-lines">{display(key, v, report.meta.businessZone)}</span>
              ),
            }))}
            scroll={{ x: cols.length * 140 }}
            pagination={{
              current: page,
              pageSize: size,
              total: report.total,
              showSizeChanger: true,
              onChange: (p, s) => {
                setPage(p);
                setSize(s);
              },
            }}
            locale={{
              emptyText: (
                <Empty description="无符合条件的记录，仍可导出空报表" />
              ),
            }}
          />
        ) : (
          <>
            <div className="operation-cards">
              {report.records.map((r, i) => (
                <article key={i}>
                  <strong>{r.materialName}</strong>
                  <p>
                    {r.materialCode} · {r.currency}
                  </p>
                  <p>
                    领用 {display("issueQuantity", r.issueQuantity)} / 归还{" "}
                    {display("returnQuantity", r.returnQuantity)} {r.unit}
                  </p>
                  <p>
                    净消耗 {display("netQuantity", r.netQuantity)} · 金额{" "}
                    {display("netAmount", r.netAmount)}
                  </p>
                  {type !== "semester" && (
                    <p>
                      {r.issueNo} · {r.recipientName} · {r.projectNo}
                    </p>
                  )}
                </article>
              ))}
            </div>
            {!report.records.length && (
              <Empty description="暂无符合条件的记录" />
            )}
            <div className="report-mobile-pagination">
              <Button
                disabled={page <= 1}
                onClick={() => setPage((p) => p - 1)}
              >
                上一页
              </Button>
              <span>
                {page} / {Math.max(1, Math.ceil(report.total / size))}
              </span>
              <Button
                disabled={page * size >= report.total}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </>
        )}
      </section>
      {report && (
        <Alert
          className="report-notes"
          type="info"
          message="统计口径"
          description={
            <ul>
              {report.meta.notes.map((n) => (
                <li key={n}>{n}</li>
              ))}
              <li>
                导出上限 10,000 行。经费表为通用模板，尚未针对特定财务系统验证。
              </li>
            </ul>
          }
        />
      )}
    </div>
  );
}
