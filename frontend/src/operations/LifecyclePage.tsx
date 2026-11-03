import { useEffect, useRef, useState } from "react";
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Skeleton,
  Space,
  Tabs,
  Tag,
} from "antd";
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import Decimal from "decimal.js";
import { Link, useSearchParams } from "react-router-dom";
import { ApiError, newRequestId, request } from "../api";
import { useAuth } from "../auth";
import { dateTime, quantity } from "../materials/types";
import { MaterialSearch } from "../issues/IssueCreator";
import type { IssueDetail, IssueOrder } from "../issues/types";
import type { Batch, ItemBatches, Page } from "../stock/types";
import { query } from "../stock/types";
import { AuthPhoto, LoadError, Records, useRemote } from "./shared";
import "./operations.css";

interface ReturnOrder {
  id: number;
  returnNo: string;
  issueNo: string;
  issueOrderId: number;
  recipientName: string;
  accountName: string;
  operatorName: string;
  projectNo: string;
  course?: string;
  remark?: string;
  createdAt: string;
}
interface ReturnLine {
  id: number;
  materialName: string;
  materialCode: string;
  batchCode: string;
  quantity: string;
  unit: string;
  issuedQuantity: string;
  returnedQuantity: string;
  transactionNo: string;
  beforeQuantity: string;
  afterQuantity: string;
}
interface ReturnDetail {
  order: ReturnOrder;
  lines: ReturnLine[];
}
interface Scrap {
  id: number;
  scrapNo: string;
  materialName: string;
  materialCode: string;
  batchCode: string;
  quantity: string;
  unit: string;
  reason: string;
  remark?: string;
  operatorName: string;
  createdAt: string;
  attachmentId?: number;
  beforeQuantity: string;
  afterQuantity: string;
  transactionNo: string;
}
type Pending = { path: string; body: Record<string, unknown> };
function readPending(key: string): Pending | undefined {
  try {
    const p = JSON.parse(sessionStorage.getItem(key) || "null");
    if (
      p &&
      ["/api/returns", "/api/scraps"].includes(p.path) &&
      p.body?.idempotencyKey
    )
      return p;
  } catch {
    /* invalid session cache */
  }
}
export function LifecyclePage() {
  const { user } = useAuth(),
    { message } = App.useApp(),
    [params, setParams] = useSearchParams(),
    manage = !!user?.roles.some((r) => ["ADMIN", "LAB_TECH"].includes(r)),
    storage = "caizhitong.pending-lifecycle:" + user!.id;
  const canScrap = !!user?.modules.includes("inventory");
  const kind = params.get("tab") === "scrap" && canScrap ? "scrap" : "return",
    endpoint = kind === "scrap" ? "/api/scraps" : "/api/returns";
  const [page, setPage] = useState(1),
    [size, setSize] = useState(20),
    [search, setSearch] = useState(""),
    [keyword, setKeyword] = useState(""),
    [person, setPerson] = useState(""),
    [create, setCreate] = useState(false),
    [pending, setPending] = useState<Pending | undefined>(() =>
      readPending(storage),
    ),
    [busy, setBusy] = useState(false),
    [failure, setFailure] = useState(""),
    [editable, setEditable] = useState(false);
  const lock = useRef(false),
    remote = useRemote<Page<ReturnOrder | Scrap>>(
      endpoint + query({ page, size, keyword, user: person }),
    ),
    selected = Number(params.get("record")) || undefined;
  async function commit(p: Pending) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    setEditable(false);
    sessionStorage.setItem(storage, JSON.stringify(p));
    setPending(p);
    try {
      await request(p.path, { method: "POST", body: JSON.stringify(p.body) });
      sessionStorage.removeItem(storage);
      setPending(undefined);
      setCreate(false);
      remote.reload();
      void message.success(
        p.path === "/api/returns"
          ? "归还成功，已增加原批次库存"
          : "报废成功，库存与流水已同步",
      );
    } catch (e) {
      setFailure((e as Error).message);
      setEditable(e instanceof ApiError && [400, 404, 409].includes(e.status));
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  function detail(id?: number) {
    const p = new URLSearchParams(params);
    if (id) p.set("record", String(id));
    else p.delete("record");
    setParams(p);
  }
  return (
    <div className="page-enter operations-page">
      <div className="page-heading">
        <div>
          <h1>归还与报废</h1>
          <p>完好材料回到原批次，仓库损耗留下可追溯记录。</p>
        </div>
        <Space>
          <Button
            aria-label="刷新流转记录"
            icon={<ReloadOutlined />}
            onClick={remote.reload}
          />
          {manage && (
            <Button
              aria-label={kind === "return" ? "登记归还" : "登记报废"}
              type="primary"
              icon={<PlusOutlined />}
              disabled={!!pending}
              onClick={() => setCreate(true)}
            >
              {kind === "return" ? "登记归还" : "登记报废"}
            </Button>
          )}
        </Space>
      </div>
      {pending && (
        <Alert
          className="form-alert"
          showIcon
          type="warning"
          message="有一笔结果待确认的库存操作"
          description={
            failure ||
            "请安全重试原请求以核对结果。请求标识已保存，刷新或关闭面板后仍可继续。"
          }
          action={
            <Space>
              {editable && (
                <Button
                  onClick={() => {
                    sessionStorage.removeItem(storage);
                    setPending(undefined);
                    setFailure("");
                  }}
                >
                  修改后重新登记
                </Button>
              )}
              <Button loading={busy} onClick={() => void commit(pending)}>
                安全重试原请求
              </Button>
            </Space>
          }
        />
      )}
      <Tabs
        activeKey={kind}
        onChange={(k) => {
          setParams({ tab: k });
          setPage(1);
          setSearch("");
          setKeyword("");
          setPerson("");
        }}
        items={[
          { key: "return", label: manage ? "归还记录" : "我的归还" },
          ...(canScrap ? [{ key: "scrap", label: "报废记录" }] : []),
        ]}
      />
      <div className="operation-note">
        {kind === "return"
          ? "仅登记完好可用材料。累计归还不超过原领用量，归还不冲回月度领用额度。"
          : "仅扣减仓库当前余额。已领出的损坏或丢失材料，不再次减少仓库库存。"}
      </div>
      <section className="surface operation-panel">
        <div className="operation-filter">
          <Input.Search
            aria-label="搜索流转记录"
            placeholder={
              kind === "return"
                ? "归还单号、原领用单号或材料"
                : "报废单号、材料或批次"
            }
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => {
              setKeyword(v);
              setPage(1);
            }}
            allowClear
          />
          {manage && kind === "return" && (
            <Input.Search
              aria-label="筛选归还领用人"
              placeholder="按领用人搜索"
              onSearch={(v) => {
                setPerson(v);
                setPage(1);
              }}
              allowClear
            />
          )}
        </div>
        <LoadError error={remote.error} retry={remote.reload} />
        <Records<ReturnOrder | Scrap>
          data={remote.data}
          loading={remote.loading}
          page={page}
          size={size}
          onPage={(p, s) => {
            setPage(p);
            setSize(s);
          }}
          columns={[
            {
              title: "业务单号",
              render: (_, r) => (
                <Button type="link" onClick={() => detail(r.id)}>
                  {"returnNo" in r ? r.returnNo : r.scrapNo}
                </Button>
              ),
            },
            {
              title: kind === "return" ? "原领用单 / 领用人" : "材料 / 批次",
              render: (_, r) =>
                "returnNo" in r ? (
                  <div>
                    {r.issueNo}
                    <small className="operation-sub">
                      {r.recipientName} · {r.accountName}
                    </small>
                  </div>
                ) : (
                  <div>
                    {r.materialName}
                    <small className="operation-sub">{r.batchCode}</small>
                  </div>
                ),
            },
            {
              title: kind === "return" ? "项目" : "报废数量",
              render: (_, r) =>
                "returnNo" in r
                  ? r.projectNo
                  : quantity(r.quantity) + " " + r.unit,
            },
            { title: "操作人", dataIndex: "operatorName" },
            { title: "登记时间", dataIndex: "createdAt", render: dateTime },
            { title: "状态", render: () => <Tag color="success">已完成</Tag> },
            {
              title: "操作",
              render: (_, r) => (
                <Button type="link" onClick={() => detail(r.id)}>
                  查看详情
                </Button>
              ),
            },
          ]}
          card={(r) => (
            <>
              <Button type="link" onClick={() => detail(r.id)}>
                {"returnNo" in r ? r.returnNo : r.scrapNo}
              </Button>
              <p>
                {"returnNo" in r
                  ? `${r.issueNo} · ${r.recipientName}`
                  : `${r.materialName} · ${quantity(r.quantity)} ${r.unit}`}
              </p>
              <small>
                {r.operatorName} · {dateTime(r.createdAt)}
              </small>
            </>
          )}
        />
      </section>
      {create &&
        !pending &&
        (kind === "return" ? (
          <ReturnCreator
            onClose={() => setCreate(false)}
            onSubmit={(b) =>
              void commit({
                path: endpoint,
                body: { ...b, idempotencyKey: newRequestId() },
              })
            }
          />
        ) : (
          <ScrapCreator
            onClose={() => setCreate(false)}
            onSubmit={(b) =>
              void commit({
                path: endpoint,
                body: { ...b, idempotencyKey: newRequestId() },
              })
            }
          />
        ))}
      {selected && (
        <LifecycleDetail kind={kind} id={selected} onClose={() => detail()} />
      )}
    </div>
  );
}
function ReturnCreator({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [term, setTerm] = useState(""),
    [person, setPerson] = useState(""),
    [source, setSource] = useState<number>(),
    [selected, setSelected] = useState<IssueDetail>(),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(false),
    [quantities, setQuantities] = useState<Record<number, string>>({}),
    [remark, setRemark] = useState("");
  const orders = useRemote<Page<IssueOrder>>(
    "/api/issues" + query({ keyword: term, user: person, size: 30 }),
  );
  useEffect(() => {
    if (!source) return;
    let active = true;
    setLoading(true);
    setError("");
    setSelected(undefined);
    setQuantities({});
    request<IssueDetail>("/api/issues/" + source)
      .then((d) => {
        if (active) setSelected(d);
      })
      .catch((e) => {
        if (active) setError((e as Error).message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [source]);
  function submit() {
    const lines = selected?.lines
      .filter((l) => new Decimal(quantities[l.id] || 0).gt(0))
      .map((l) => ({ issueOrderLineId: l.id, quantity: quantities[l.id] }));
    if (!lines?.length) {
      setError("请至少填写一条本次归还数量");
      return;
    }
    if (
      selected?.lines.some((l) =>
        new Decimal(quantities[l.id] || 0).gt(l.returnableQuantity),
      )
    ) {
      setError("本次归还数量超过剩余可归还量");
      return;
    }
    onSubmit({ issueOrderId: source, lines, remark });
  }
  return (
    <Drawer
      open
      title="登记完好材料归还"
      width="min(760px,100vw)"
      onClose={onClose}
      footer={
        <div className="drawer-actions">
          <Button onClick={onClose}>取消登记</Button>
          <Button
            type="primary"
            disabled={!selected || loading}
            onClick={submit}
          >
            确认归还并入库
          </Button>
        </div>
      }
    >
      <p className="material-form-note">
        从真实领用记录选择明细。归还将自动写入原批次，无需另选批次。
      </p>
      <div className="operation-filter">
        <Input.Search
          placeholder="搜索领用单号或材料"
          aria-label="查找原领用单"
          onSearch={setTerm}
        />
        <Input.Search
          placeholder="搜索领用人"
          aria-label="查找原领用人"
          onSearch={setPerson}
        />
      </div>
      <LoadError error={orders.error} retry={orders.reload} />
      <Form layout="vertical">
        <Form.Item label="原始领用单" required>
          <Select
            aria-label="选择原领用单"
            value={source}
            onChange={setSource}
            virtual={false}
            loading={orders.loading}
            options={orders.data?.records.map((o) => ({
              value: o.id,
              label: `${o.issueNo} · ${o.recipientName} · ${o.materialNames}`,
            }))}
            placeholder="请选择原领用单（最多显示 30 条，请搜索缩小范围）"
          />
        </Form.Item>
      </Form>
      {error && (
        <Alert className="form-alert" type="error" message={error} showIcon />
      )}
      {loading && <Skeleton active />}
      {selected && (
        <>
          <div className="detail-notice">
            领用人：{selected.order.recipientName} · 项目：
            {selected.order.projectNo}
            <br />
            课程：{selected.order.course || "未关联课程"}
          </div>
          {selected.lines.map((l) => (
            <section key={l.id} className="return-line">
              <h3>{l.materialName}</h3>
              <Link to={"/stock/batches/" + l.batchCode}>{l.batchCode}</Link>
              <div className="return-amounts">
                <span>
                  原领用 <b>{quantity(l.quantity)}</b>
                </span>
                <span>
                  已归还 <b>{quantity(l.returnedQuantity)}</b>
                </span>
                <span>
                  可归还{" "}
                  <b>
                    {quantity(l.returnableQuantity)} {l.unit}
                  </b>
                </span>
              </div>
              <label htmlFor={"return-" + l.id}>本次归还数量</label>
              <InputNumber
                id={"return-" + l.id}
                stringMode
                min="0"
                max={l.returnableQuantity}
                precision={3}
                disabled={new Decimal(l.returnableQuantity).lte(0)}
                value={quantities[l.id]}
                onChange={(v) =>
                  setQuantities((q) => ({ ...q, [l.id]: v || "0" }))
                }
              />
            </section>
          ))}
          <label htmlFor="return-remark">归还备注</label>
          <Input.TextArea
            id="return-remark"
            rows={3}
            maxLength={2000}
            value={remark}
            onChange={(e) => setRemark(e.target.value)}
          />
        </>
      )}
    </Drawer>
  );
}
function ScrapCreator({
  onClose,
  onSubmit,
}: {
  onClose: () => void;
  onSubmit: (body: Record<string, unknown>) => void;
}) {
  const [form] = Form.useForm(),
    [material, setMaterial] = useState<number>(),
    [batch, setBatch] = useState<Batch>(),
    [batchPage, setBatchPage] = useState(1),
    [options, setOptions] = useState<Batch[]>([]),
    [uploading, setUploading] = useState(false),
    [attachment, setAttachment] = useState<number>(),
    [fileName, setFileName] = useState(""),
    [error, setError] = useState("");
  const batches = useRemote<ItemBatches>(
    "/api/stocks/" +
      (material || 0) +
      "/batches" +
      query({ page: batchPage, size: 50 }),
    !!material,
  );
  useEffect(() => {
    if (batches.data)
      setOptions((prev) =>
        batchPage === 1
          ? batches.data!.batches.records
          : [...prev, ...batches.data!.batches.records],
      );
  }, [batches.data, batchPage]);
  async function upload(file?: File) {
    setAttachment(undefined);
    setFileName("");
    if (!file) return;
    if (file.size > 5242880) {
      setError("照片大小不能超过 5 MB");
      return;
    }
    setUploading(true);
    setError("");
    const data = new FormData();
    data.append("file", file);
    try {
      const saved = await request<{ id: number }>("/api/scraps/attachments", {
        method: "POST",
        body: data,
      });
      setAttachment(saved.id);
      setFileName(file.name);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploading(false);
    }
  }
  return (
    <Drawer
      open
      title="登记仓库报废"
      width="min(650px,100vw)"
      onClose={onClose}
      footer={
        <div className="drawer-actions">
          <Button disabled={uploading} onClick={onClose}>
            取消登记
          </Button>
          <Button
            type="primary"
            disabled={!batch || uploading || !!error}
            onClick={() => form.submit()}
          >
            确认报废并扣库
          </Button>
        </div>
      }
    >
      <p className="material-form-note">
        报废记录保存后不可修改或删除。请核对仓库批次与实际损耗数量。
      </p>
      <Form
        form={form}
        layout="vertical"
        onFinish={(v) =>
          onSubmit({ batchId: batch?.id, ...v, attachmentId: attachment })
        }
      >
        <Form.Item label="库存材料" required>
          <MaterialSearch
            includeInactive
            value={material}
            onChange={(id) => {
              setMaterial(id);
              setBatch(undefined);
              setBatchPage(1);
              setOptions([]);
            }}
          />
        </Form.Item>
        {material && (
          <>
            <LoadError error={batches.error} retry={batches.reload} />
            <Form.Item label="原库存批次" required>
              <Select
                aria-label="选择报废批次"
                virtual={false}
                value={batch?.id}
                onChange={(id) => setBatch(options.find((b) => b.id === id))}
                loading={batches.loading}
                options={options
                  .filter((b) => new Decimal(b.quantity).gt(0))
                  .map((b) => ({
                    value: b.id,
                    label: `${b.batchCode} · ${quantity(b.quantity)} ${b.unit}`,
                  }))}
                placeholder="选择有库存的批次"
              />
            </Form.Item>
            {options.length < (batches.data?.batches.total || 0) && (
              <Button onClick={() => setBatchPage((p) => p + 1)}>
                加载更多批次
              </Button>
            )}
          </>
        )}
        {batch && (
          <div className="detail-notice">
            {batch.itemName} · {batch.batchCode}
            <br />
            仓库当前可用：{quantity(batch.quantity)} {batch.unit}
          </div>
        )}
        <Form.Item
          label="报废数量"
          name="quantity"
          rules={[
            { required: true, message: "请输入报废数量" },
            {
              validator: async (_, v) => {
                if (
                  v &&
                  (new Decimal(v).lte(0) ||
                    (batch && new Decimal(v).gt(batch.quantity)))
                )
                  throw new Error("数量必须大于零且不超过仓库余额");
              },
            },
          ]}
        >
          <InputNumber
            stringMode
            min="0.001"
            max={batch?.quantity}
            precision={3}
            style={{ width: "100%" }}
          />
        </Form.Item>
        <Form.Item
          label="报废原因"
          name="reason"
          rules={[
            { required: true, whitespace: true, message: "请填写报废原因" },
            { max: 1000 },
          ]}
        >
          <Input.TextArea rows={3} maxLength={1000} />
        </Form.Item>
        <Form.Item label="备注（选填）" name="remark">
          <Input.TextArea rows={2} maxLength={2000} />
        </Form.Item>
        <Form.Item label="损耗照片（选填，最大 5 MB）">
          <input
            aria-label="上传损耗照片"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            disabled={uploading}
            onChange={(e) => void upload(e.target.files?.[0])}
          />
          <div className="operation-sub">
            {uploading
              ? "正在校验并保存照片…"
              : fileName
                ? "已保存：" + fileName
                : "支持 JPG、PNG、WebP，实际文件类型由服务端校验。"}
          </div>
          {attachment && (
            <>
              <AuthPhoto id={attachment} />
              <Button
                onClick={() => {
                  setAttachment(undefined);
                  setFileName("");
                  setError("");
                }}
              >
                移除照片关联
              </Button>
            </>
          )}
        </Form.Item>
        {error && (
          <Alert
            type="error"
            message={error}
            action={
              <Button
                onClick={() => {
                  setError("");
                  setAttachment(undefined);
                }}
              >
                不使用照片继续
              </Button>
            }
          />
        )}
      </Form>
    </Drawer>
  );
}
export function LifecycleDetail({
  kind,
  id,
  onClose,
}: {
  kind: string;
  id: number;
  onClose: () => void;
}) {
  const remote = useRemote<ReturnDetail & Scrap>(
      (kind === "scrap" ? "/api/scraps/" : "/api/returns/") + id,
    ),
    d = remote.data;
  return (
    <Drawer
      open
      title={kind === "scrap" ? "报废记录详情" : "归还记录详情"}
      width="min(780px,100vw)"
      onClose={onClose}
    >
      <LoadError error={remote.error} retry={remote.reload} />
      {remote.loading ? (
        <Skeleton active />
      ) : (
        d &&
        (kind === "scrap" ? (
          <>
            <h2>{d.scrapNo}</h2>
            <Descriptions
              column={1}
              items={[
                {
                  key: "m",
                  label: "材料",
                  children: d.materialName + " · " + d.materialCode,
                },
                {
                  key: "b",
                  label: "库存批次",
                  children: (
                    <Link to={"/stock/batches/" + d.batchCode}>
                      {d.batchCode}
                    </Link>
                  ),
                },
                {
                  key: "q",
                  label: "报废数量",
                  children: quantity(d.quantity) + " " + d.unit,
                },
                {
                  key: "r",
                  label: "原因",
                  children: <span className="preserve-lines">{d.reason}</span>,
                },
                { key: "note", label: "备注", children: d.remark || "无" },
                { key: "o", label: "操作人", children: d.operatorName },
                { key: "t", label: "时间", children: dateTime(d.createdAt) },
                { key: "ledger", label: "库存流水", children: d.transactionNo },
                {
                  key: "change",
                  label: "批次余额",
                  children:
                    quantity(d.beforeQuantity) +
                    " → " +
                    quantity(d.afterQuantity),
                },
              ]}
            />
            {d.attachmentId && <AuthPhoto id={d.attachmentId} />}
          </>
        ) : (
          <>
            <h2>{d.order.returnNo}</h2>
            <Descriptions
              column={1}
              items={[
                {
                  key: "s",
                  label: "原领用单",
                  children: (
                    <Link to={"/requisitions?order=" + d.order.issueOrderId}>
                      {d.order.issueNo}
                    </Link>
                  ),
                },
                { key: "p", label: "领用人", children: d.order.recipientName },
                { key: "o", label: "操作人", children: d.order.operatorName },
                {
                  key: "t",
                  label: "时间",
                  children: dateTime(d.order.createdAt),
                },
                { key: "r", label: "备注", children: d.order.remark || "无" },
              ]}
            />
            {d.lines.map((l) => (
              <section className="return-line" key={l.id}>
                <h3>{l.materialName}</h3>
                <Link to={"/stock/batches/" + l.batchCode}>{l.batchCode}</Link>
                <p>
                  本次归还 {quantity(l.quantity)} {l.unit} · 累计已归还{" "}
                  {quantity(l.returnedQuantity)} / 原领用{" "}
                  {quantity(l.issuedQuantity)}
                </p>
                <small>
                  {l.transactionNo} · 批次余额 {quantity(l.beforeQuantity)} →{" "}
                  {quantity(l.afterQuantity)}
                </small>
              </section>
            ))}
          </>
        ))
      )}
    </Drawer>
  );
}
