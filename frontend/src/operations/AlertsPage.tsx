import { MotionDrawer as Drawer, DrawerPresence } from '../motion/MotionDrawer'
import { useEffect, useState } from "react";
import { Alert, App, Button, Descriptions, Form, Input, Select, Skeleton, Space, Tag } from 'antd'
import { ReloadOutlined } from "@ant-design/icons";
import { useSearchParams, Link } from "react-router-dom";
import { request } from "../api";
import { useAuth } from "../auth";
import { dateTime, quantity } from "../materials/types";
import type { Page } from "../stock/types";
import { query } from "../stock/types";
import { LoadError, Records, useRemote } from "./shared";
import "./operations.css";
interface InventoryAlert {
  id: number;
  materialId: number;
  materialName: string;
  materialCode: string;
  unit: string;
  status: string;
  level: string;
  currentQuantity: string;
  currentSafeStock: string;
  triggerQuantity: string;
  triggerSafeStock: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
  acknowledgedName?: string;
  handlingNote?: string;
  acknowledgedAt?: string;
  mailStatus: string;
  failureReason?: string;
  mailAttempts: number;
  recipients?: string;
  sentAt?: string;
}
const states: Record<string, string> = {
  ACTIVE: "待处理",
  ACKNOWLEDGED: "已确认",
  RESOLVED: "已恢复",
};
const mails: Record<string, string> = {
  PENDING: "待发送",
  NOT_CONFIGURED: "未配置",
  SENDING: "发送中",
  SENT: "已发送",
  FAILED: "发送失败",
};
function status(a: InventoryAlert) {
  return (
    <Tag
      color={
        a.status === "RESOLVED"
          ? "success"
          : a.status === "ACTIVE"
            ? "warning"
            : "processing"
      }
    >
      {states[a.status]}
    </Tag>
  );
}
export function AlertsPage() {
  const { user } = useAuth(),
    { message } = App.useApp(),
    [params, setParams] = useSearchParams(),
    manage = !!user?.roles.some((r) => ["ADMIN", "LAB_TECH"].includes(r)),
    handle = manage || !!user?.roles.includes("PURCHASER");
  const [page, setPage] = useState(1),
    [size, setSize] = useState(20),
    [keyword, setKeyword] = useState(""),
    [state, setState] = useState<string>(),
    [scanning, setScanning] = useState(false);
  const data = useRemote<Page<InventoryAlert>>(
      "/api/alerts" + query({ page, size, keyword, status: state }),
    ),
    counts = useRemote<{
      lowMaterialCount: number;
      pendingCount: number;
      acknowledgedCount: number;
    }>("/api/alerts/summary"),
    selected = Number(params.get("event")) || undefined;
  function refresh() {
    data.reload();
    counts.reload();
  }
  async function rescan() {
    setScanning(true);
    try {
      await request("/api/alerts/rescan", { method: "POST" });
      refresh();
      void message.success("库存扫描完成，预警已同步");
    } catch (e) {
      void message.error((e as Error).message);
    } finally {
      setScanning(false);
    }
  }
  return (
    <div className="page-enter operations-page">
      <div className="page-heading">
        <div>
          <h1>库存预警</h1>
          <p>关注安全库存，及时协调补充。</p>
        </div>
        <Space>
          <Button
            aria-label="刷新预警"
            icon={<ReloadOutlined />}
            onClick={refresh}
          />
          {manage && (
            <Button onClick={() => void rescan()} loading={scanning}>
              重新扫描库存
            </Button>
          )}
        </Space>
      </div>
      <LoadError error={counts.error} retry={counts.reload} />
      <section className="surface operation-metrics" aria-label="库存预警指标">
        <div>
          <span>当前低库存材料</span>
          <strong>
            {counts.loading ? "…" : (counts.data?.lowMaterialCount ?? "—")}
            <small>种</small>
          </strong>
        </div>
        <div>
          <span>待处理预警</span>
          <strong>
            {counts.loading ? "…" : (counts.data?.pendingCount ?? "—")}
            <small>项</small>
          </strong>
        </div>
        <div>
          <span>已确认待恢复</span>
          <strong>
            {counts.loading ? "…" : (counts.data?.acknowledgedCount ?? "—")}
            <small>项</small>
          </strong>
        </div>
      </section>
      <div className="operation-note">
        总库存严格小于安全库存时触发。安全库存为 0
        不预警；库存恢复后自动解除，同一轮低库存只登记一次通知。已恢复记录保留恢复时快照。
      </div>
      <section className="surface operation-panel">
        <div className="operation-filter">
          <Input.Search
            aria-label="搜索预警材料"
            placeholder="材料名称或编码"
            allowClear
            onSearch={(v) => {
              setKeyword(v);
              setPage(1);
            }}
          />
          <Select
            aria-label="预警处理状态"
            allowClear
            placeholder="全部处理状态"
            virtual={false}
            value={state}
            options={Object.entries(states).map(([value, label]) => ({
              value,
              label,
            }))}
            onChange={(v) => {
              setState(v);
              setPage(1);
            }}
          />
        </div>
        <LoadError error={data.error} retry={data.reload} />
        <Records<InventoryAlert>
          data={data.data}
          loading={data.loading}
          page={page}
          size={size}
          onPage={(p, s) => {
            setPage(p);
            setSize(s);
          }}
          columns={[
            {
              title: "材料",
              render: (_, a) => (
                <div>
                  <strong>{a.materialName}</strong>
                  <small className="operation-sub">{a.materialCode}</small>
                </div>
              ),
            },
            {
              title: "最近检测库存 / 安全库存",
              render: (_, a) =>
                quantity(a.currentQuantity) +
                " / " +
                quantity(a.currentSafeStock) +
                " " +
                a.unit,
            },
            {
              title: "级别",
              render: (_, a) => (
                <Tag color={a.level === "CRITICAL" ? "error" : "default"}>
                  {a.level === "CRITICAL" ? "库存耗尽" : "低库存"}
                </Tag>
              ),
            },
            { title: "状态", render: (_, a) => status(a) },
            { title: "首次预警", dataIndex: "createdAt", render: dateTime },
            {
              title: "邮件",
              render: (_, a) => mails[a.mailStatus] || "未发送",
            },
            {
              title: "操作",
              render: (_, a) => (
                <Button
                  type="link"
                  onClick={() => setParams({ event: String(a.id) })}
                >
                  查看 / 处理
                </Button>
              ),
            },
          ]}
          card={(a) => (
            <>
              <div className="operation-card-title">
                <strong>{a.materialName}</strong>
                {status(a)}
              </div>
              <p>{a.materialCode}</p>
              <p>
                库存 {quantity(a.currentQuantity)} / 安全库存{" "}
                {quantity(a.currentSafeStock)} {a.unit}
              </p>
              <small>
                {dateTime(a.createdAt)} · 邮件{mails[a.mailStatus]}
              </small>
              <Button
                type="link"
                onClick={() => setParams({ event: String(a.id) })}
              >
                查看 / 处理
              </Button>
            </>
          )}
        />
      </section>
      <DrawerPresence>{selected && (
        <AlertDetail
          id={selected}
          manage={manage}
          handle={handle}
          onClose={() => setParams({})}
          onSaved={refresh}
        />
      )}</DrawerPresence>
    </div>
  );
}
function AlertDetail({
  id,
  manage,
  handle,
  onClose,
  onSaved,
}: {
  id: number;
  manage: boolean;
  handle: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const data = useRemote<InventoryAlert>("/api/alerts/" + id),
    [busy, setBusy] = useState(false),
    [failure, setFailure] = useState(""),
    [form] = Form.useForm(),
    { message } = App.useApp(),
    a = data.data;
  useEffect(() => {
    if (a) form.setFieldsValue({ note: a.handlingNote });
  }, [a, form]);
  async function action(path: string, body?: object) {
    setBusy(true);
    setFailure("");
    try {
      await request(path, {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
      });
      data.reload();
      onSaved();
      void message.success(
        body ? "预警处理备注已保存" : "通知已重新排队，请稍后刷新查看发送结果",
      );
    } catch (e) {
      setFailure((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      open
      title="库存预警详情"
      onClose={onClose}
      width="min(680px,100vw)"
    >
      <LoadError error={data.error} retry={data.reload} />
      {failure && (
        <Alert className="form-alert" type="error" message={failure} showIcon />
      )}
      {data.loading ? (
        <Skeleton active />
      ) : (
        a && (
          <>
            <div className="operation-card-title">
              <h2>{a.materialName}</h2>
              {status(a)}
            </div>
            <p className="operation-sub">{a.materialCode}</p>
            <Descriptions
              column={1}
              items={[
                {
                  key: "current",
                  label: a.status === "RESOLVED" ? "恢复时库存 / 安全库存" : "当前库存 / 安全库存",
                  children:
                    quantity(a.currentQuantity) +
                    " / " +
                    quantity(a.currentSafeStock) +
                    " " +
                    a.unit,
                },
                {
                  key: "trigger",
                  label: "首次触发库存 / 阈值",
                  children:
                    quantity(a.triggerQuantity) +
                    " / " +
                    quantity(a.triggerSafeStock),
                },
                {
                  key: "created",
                  label: "首次预警时间",
                  children: dateTime(a.createdAt),
                },
                {
                  key: "updated",
                  label: "最后检测时间",
                  children: dateTime(a.updatedAt),
                },
                {
                  key: "resolved",
                  label: "恢复时间",
                  children: a.resolvedAt ? dateTime(a.resolvedAt) : "尚未恢复",
                },
                {
                  key: "handler",
                  label: "处理人",
                  children: a.acknowledgedName || "尚未确认",
                },
                {
                  key: "note",
                  label: "处理备注",
                  children: a.handlingNote || "无",
                },
              ]}
            />
            <Link
              to={"/inventory?keyword=" + encodeURIComponent(a.materialCode)}
            >
              查看库存
            </Link>
            <h3 className="operation-section-title">邮件通知</h3>
            <Descriptions
              column={1}
              items={[
                {
                  key: "state",
                  label: "发送状态",
                  children: mails[a.mailStatus] || "未发送",
                },
                { key: "attempt", label: "尝试次数", children: a.mailAttempts },
                {
                  key: "target",
                  label: "收件人",
                  children: a.recipients || "尚未配置有效邮箱",
                },
                {
                  key: "fail",
                  label: "未发送原因",
                  children: a.failureReason || "无",
                },
                {
                  key: "time",
                  label: "发送时间",
                  children: a.sentAt ? dateTime(a.sentAt) : "尚未发送",
                },
              ]}
            />
            {a.mailStatus === "NOT_CONFIGURED" && (
              <Alert
                type="info"
                showIcon
                message="站内预警正常可用，邮件尚未发送"
                description="配置 SMTP 和有效收件人后，管理员或实验员可重试通知。"
              />
            )}
            {manage && ["FAILED", "NOT_CONFIGURED"].includes(a.mailStatus) && (
              <Button
                className="operation-action"
                loading={busy}
                onClick={() => void action("/api/alerts/" + id + "/mail-retry")}
              >
                重试邮件通知
              </Button>
            )}
            {handle && a.status !== "RESOLVED" && (
              <Form
                form={form}
                layout="vertical"
                className="operation-action"
                onFinish={(v) =>
                  void action("/api/alerts/" + id + "/acknowledge", v)
                }
              >
                <Form.Item
                  label="处理备注"
                  name="note"
                  rules={[
                    {
                      required: true,
                      whitespace: true,
                      message: "请填写处理备注",
                    },
                    { max: 2000 },
                  ]}
                >
                  <Input.TextArea
                    rows={3}
                    maxLength={2000}
                    placeholder="例如：已核对库存，正在联系采购补充"
                  />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={busy}>
                  确认预警并保存备注
                </Button>
              </Form>
            )}
          </>
        )
      )}
    </Drawer>
  );
}
