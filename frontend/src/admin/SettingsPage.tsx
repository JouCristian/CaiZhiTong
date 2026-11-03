import { useEffect, useState } from "react";
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import { ReloadOutlined, SaveOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { request } from "../api";
import { useSystem } from "../system";
import { dateTime } from "../materials/types";
import { LoadError, useRemote } from "../operations/shared";
import "./admin.css";
interface Settings {
  revision: number;
  systemName: string;
  systemDescription: string;
  businessZone: string;
  version: string;
  academicYear: number;
  autumnStart: string;
  autumnEnd: string;
  springStart: string;
  springEnd: string;
  notificationRecipients: string[];
  effectiveRecipients: string[];
  zoneLocked: boolean;
  timeZones: string[];
  smtpConfigured: boolean;
  notificationDispatchEnabled: boolean;
  notificationStatus: string;
  notificationReason?: string;
  updatedAt: string;
  recentNotifications: {
    status: string;
    attempts: number;
    failureReason?: string;
    materialName: string;
    createdAt: string;
    sentAt?: string;
  }[];
}
interface Values {
  systemName: string;
  systemDescription: string;
  businessZone: string;
  academicYear: number;
  autumn: [dayjs.Dayjs, dayjs.Dayjs];
  spring: [dayjs.Dayjs, dayjs.Dayjs];
  notificationRecipients: string[];
}
const states: Record<string, string> = {
  NOT_CONFIGURED: "未配置",
  READY: "已就绪",
  DISABLED: "发送已关闭",
  PENDING: "待发送",
  SENDING: "发送中",
  SENT: "已发送",
  FAILED: "发送失败",
};
export function SettingsPage() {
  const remote = useRemote<Settings>("/api/settings"),
    { refresh } = useSystem(),
    { message } = App.useApp(),
    [form] = Form.useForm<Values>(),
    [saving, setSaving] = useState(false),
    [error, setError] = useState(""),
    d = remote.data;
  useEffect(() => {
    if (d)
      form.setFieldsValue({
        ...d,
        autumn: [dayjs(d.autumnStart), dayjs(d.autumnEnd)],
        spring: [dayjs(d.springStart), dayjs(d.springEnd)],
      });
  }, [d, form]);
  async function save(v: Values) {
    if (!d) return;
    setSaving(true);
    setError("");
    try {
      await request("/api/settings", {
        method: "PUT",
        body: JSON.stringify({
          revision: d.revision,
          systemName: v.systemName.trim(),
          systemDescription: v.systemDescription.trim(),
          businessZone: v.businessZone,
          academicYear: v.academicYear,
          autumnStart: v.autumn[0].format("YYYY-MM-DD"),
          autumnEnd: v.autumn[1].format("YYYY-MM-DD"),
          springStart: v.spring[0].format("YYYY-MM-DD"),
          springEnd: v.spring[1].format("YYYY-MM-DD"),
          notificationRecipients: (v.notificationRecipients || []).map((s) =>
            s.trim(),
          ),
        }),
      });
      await refresh();
      remote.reload();
      void message.success("系统设置已保存，报表默认范围已同步");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="page-enter operations-page">
      <div className="page-heading">
        <div>
          <h1>系统设置</h1>
          <p>配置材料库基础信息、学期范围与业务通知。</p>
        </div>
        <Space>
          <Button
            aria-label="重新加载系统设置"
            icon={<ReloadOutlined />}
            onClick={() => {
              setError("");
              remote.reload();
            }}
          />
          <Button
            aria-label="保存系统设置"
            icon={<SaveOutlined />}
            type="primary"
            loading={saving}
            disabled={remote.loading || !d}
            onClick={() => form.submit()}
          >
            保存设置
          </Button>
        </Space>
      </div>
      <LoadError error={remote.error} retry={remote.reload} />
      {error && (
        <Alert className="form-alert" showIcon type="error" message={error} />
      )}
      <div className="settings-layout">
        <nav className="settings-nav" aria-label="设置分组">
          <a href="#general">基础信息</a>
          <a href="#semester">学期配置</a>
          <a href="#notifications">邮件通知</a>
          {d && (
            <small>
              版本 {d.version}
              <br />
              保存于 {dateTime(d.updatedAt)}
            </small>
          )}
        </nav>
        <Form
          form={form}
          layout="vertical"
          onFinish={save}
          disabled={saving || remote.loading}
          scrollToFirstError
          className="settings-form"
        >
          <section className="surface settings-section" id="general">
            <div className="panel-heading">
              <h2>基础信息</h2>
              <span>全局工作空间</span>
            </div>
            <Form.Item
              label="系统名称"
              name="systemName"
              rules={[
                { required: true, whitespace: true, message: "请输入系统名称" },
                { max: 40 },
              ]}
            >
              <Input maxLength={40} />
            </Form.Item>
            <Form.Item
              label="系统描述"
              name="systemDescription"
              rules={[
                { required: true, whitespace: true, message: "请输入系统描述" },
                { max: 200 },
              ]}
            >
              <Input.TextArea rows={2} maxLength={200} />
            </Form.Item>
            <div className="settings-fields">
              <Form.Item
                label="业务时区"
                name="businessZone"
                rules={[{ required: true }]}
                extra={
                  d?.zoneLocked
                    ? "已有库存业务，时区已锁定，避免历史统计与月度额度漂移。"
                    : "首次库存业务前可调整；保存后立即用于日期和学期统计。"
                }
              >
                <Select
                  aria-label="选择业务时区"
                  disabled={d?.zoneLocked}
                  showSearch
                  options={d?.timeZones.map((value) => ({
                    value,
                    label: value,
                  }))}
                />
              </Form.Item>
              <Form.Item label="当前版本">
                <Input
                  readOnly
                  value={d?.version || "正在读取"}
                  aria-label="当前版本"
                />
              </Form.Item>
            </div>
          </section>
          <section className="surface settings-section" id="semester">
            <div className="panel-heading">
              <h2>学期配置</h2>
              <span>报表默认范围</span>
            </div>
            <p className="material-form-note">
              学年从当年 9 月至次年 8
              月。保存的范围用于该学年的默认报表，自定义起止日期仍优先。
            </p>
            <Form.Item
              label="学年起始年份"
              name="academicYear"
              rules={[{ required: true, message: "请输入学年" }]}
              extra="切换学年会重置为标准日期，可继续调整。"
            >
              <InputNumber
                min={1990}
                max={2200}
                precision={0}
                style={{ width: "100%" }}
                onChange={(y) => {
                  if (y)
                    form.setFieldsValue({
                      autumn: [dayjs(`${y}-09-01`), dayjs(`${y + 1}-01-31`)],
                      spring: [
                        dayjs(`${y + 1}-02-01`),
                        dayjs(`${y + 1}-08-31`),
                      ],
                    });
                }}
              />
            </Form.Item>
            <Form.Item
              label="秋季学期日期范围"
              name="autumn"
              rules={[{ required: true, message: "请选择秋季起止日期" }]}
            >
              <DatePicker.RangePicker
                allowClear={false}
                style={{ width: "100%" }}
              />
            </Form.Item>
            <Form.Item
              label="春季学期日期范围"
              name="spring"
              rules={[{ required: true, message: "请选择春季起止日期" }]}
            >
              <DatePicker.RangePicker
                allowClear={false}
                style={{ width: "100%" }}
              />
            </Form.Item>
            <Alert
              showIcon
              type="info"
              message="学期日期不能重叠，秋季须先于春季，且均处于该学年内。"
            />
          </section>
          <section className="surface settings-section" id="notifications">
            <div className="panel-heading">
              <h2>邮件通知</h2>
              <Tag
                color={
                  d?.notificationStatus === "READY" ? "success" : "default"
                }
              >
                {d ? states[d.notificationStatus] : "正在读取"}
              </Tag>
            </div>
            <div className="mail-config-summary">
              <div>
                <span>SMTP</span>
                <strong>
                  {d?.smtpConfigured ? "已配置主机与发件人" : "尚未配置"}
                </strong>
              </div>
              <div>
                <span>通知发送</span>
                <strong>
                  {d?.notificationDispatchEnabled ? "已启用" : "已关闭"}
                </strong>
              </div>
            </div>
            <Alert
              className="form-alert"
              type={d?.notificationStatus === "READY" ? "success" : "info"}
              showIcon
              message={
                d?.notificationStatus === "READY"
                  ? "通知发送链路已就绪，实际结果以通知记录为准。"
                  : d?.notificationReason || "SMTP 凭据由后端环境变量管理。"
              }
              description="SMTP 密码及 JWT 密钥不会传入浏览器。无 SMTP 时站内库存预警仍正常可用。"
            />
            <Form.Item
              label="额外业务通知收件人"
              name="notificationRecipients"
              extra="与环境配置及启用采购员的邮箱合并，保存后用于新通知和显式重试。"
              rules={[
                {
                  validator: async (_, list: string[]) => {
                    if (
                      list?.length > 40 ||
                      list?.some(
                        (s) =>
                          s.length > 254 ||
                          !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim()),
                      )
                    )
                      throw new Error("请输入有效邮箱，最多 40 个");
                  },
                },
              ]}
            >
              <Select
                aria-label="业务通知收件人"
                mode="tags"
                tokenSeparators={[",", ";"]}
                virtual={false}
                placeholder="输入邮箱后回车"
              />
            </Form.Item>
            <div className="settings-effective">
              <span>当前有效收件人</span>
              <div>
                {d?.effectiveRecipients.length ? (
                  d.effectiveRecipients.map((e) => <Tag key={e}>{e}</Tag>)
                ) : (
                  <small>尚未配置有效邮箱</small>
                )}
              </div>
            </div>
            <h3 className="operation-section-title">最近通知结果</h3>
            <div className="settings-notification-table">
              <Table
                rowKey={(_, i) => String(i)}
                size="small"
                pagination={false}
                scroll={{ x: 580 }}
                dataSource={d?.recentNotifications || []}
                locale={{ emptyText: "暂无通知记录" }}
                columns={[
                  { title: "材料", dataIndex: "materialName" },
                  {
                    title: "发送状态",
                    dataIndex: "status",
                    render: (s) => states[s] || s,
                  },
                  {
                    title: "时间",
                    render: (_, n) => dateTime(n.sentAt || n.createdAt),
                  },
                  { title: "原因", render: (_, n) => n.failureReason || "无" },
                ]}
              />
            </div>
          </section>
        </Form>
      </div>
    </div>
  );
}
