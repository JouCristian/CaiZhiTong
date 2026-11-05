import { MotionDrawer as Drawer, DrawerPresence } from '../motion/MotionDrawer'
import { useState } from "react";
import { Alert, App, Button, Descriptions, Form, Input, Modal, Popconfirm, Select, Space, Switch, Tag } from 'antd'
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { request } from "../api";
import { useAuth } from "../auth";
import { roleNames } from "../modules";
import { dateTime } from "../materials/types";
import type { Page } from "../stock/types";
import { query } from "../stock/types";
import { LoadError, Records, useRemote } from "../operations/shared";
import "../operations/operations.css";
import "./admin.css";
interface Account {
  id: number;
  username: string;
  displayName: string;
  email?: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  lastLoginAt?: string;
}
interface Values {
  username: string;
  displayName: string;
  email?: string;
  roles: string[];
  enabled: boolean;
  password?: string;
}
const passwordRules = [
  { required: true, message: "请填写新密码" },
  { min: 12, max: 72, message: "密码须为 12–72 个字符" },
  {
    validator: async (_: unknown, v: string) => {
      if (v && new TextEncoder().encode(v).length > 72)
        throw new Error("密码 UTF-8 长度不能超过 72 字节");
    },
  },
];
const roles = (u: Account) => (
  <Space size={[3, 4]} wrap>
    {u.roles.map((r) => (
      <Tag key={r}>{roleNames[r] || r}</Tag>
    ))}
  </Space>
);
export function UsersPage() {
  const { user, refreshProfile } = useAuth(),
    { message } = App.useApp();
  const [filters, setFilters] = useState<{
      page: number;
      size: number;
      keyword?: string;
      enabled?: boolean;
    }>({ page: 1, size: 20 }),
    [editor, setEditor] = useState<Account | "create">(),
    [selected, setSelected] = useState<number>(),
    [passwordUser, setPasswordUser] = useState<Account>(),
    [changing, setChanging] = useState<number>();
  const remote = useRemote<Page<Account>>("/api/users" + query(filters));
  async function status(u: Account) {
    setChanging(u.id);
    try {
      await request("/api/users/" + u.id + "/status", {
        method: "PATCH",
        body: JSON.stringify({ enabled: !u.enabled }),
      });
      remote.reload();
      void message.success(
        u.enabled ? "用户已停用，旧会话已撤销" : "用户已启用",
      );
    } catch (e) {
      void message.error((e as Error).message);
    } finally {
      setChanging(undefined);
    }
  }
  const actions = (u: Account) => (
    <Space size={0} wrap>
      <Button type="link" onClick={() => setSelected(u.id)}>
        详情
      </Button>
      <Button type="link" onClick={() => setEditor(u)}>
        编辑
      </Button>
      <Button type="link" onClick={() => setPasswordUser(u)}>
        重置密码
      </Button>
      <Popconfirm
        key={u.id + ":" + u.enabled}
        title={u.enabled ? "停用此账户并撤销现有会话？" : "启用此账户？"}
        onConfirm={() => status(u)}
        okText={u.enabled ? "确认停用" : "确认启用"}
        cancelText="取消"
      >
        <Button
          aria-label={u.enabled ? "停用" : "启用"}
          type="link"
          danger={u.enabled}
          disabled={u.id === user?.id || changing === u.id}
          loading={changing === u.id}
        >
          {u.enabled ? "停用" : "启用"}
        </Button>
      </Popconfirm>
    </Space>
  );
  return (
    <div className="page-enter operations-page">
      <div className="page-heading">
        <div>
          <h1>用户与权限</h1>
          <p>维护成员账户与六类角色，让访问范围清晰可控。</p>
        </div>
        <Space>
          <Button
            aria-label="刷新用户列表"
            icon={<ReloadOutlined />}
            onClick={remote.reload}
          />
          <Button
            aria-label="新增用户"
            icon={<PlusOutlined />}
            type="primary"
            onClick={() => setEditor("create")}
          >
            新增用户
          </Button>
        </Space>
      </div>
      <div className="operation-note">
        角色权限由服务端实时校验。停用及密码重置撤销旧会话；当前管理员自身和最后一个有效管理员受保护。
      </div>
      <section className="surface operation-panel">
        <div className="operation-filter">
          <Input.Search
            aria-label="搜索用户"
            placeholder="用户名、姓名或邮箱"
            allowClear
            onSearch={(v) => setFilters((f) => ({ ...f, keyword: v, page: 1 }))}
          />
          <Select
            aria-label="用户状态筛选"
            placeholder="全部账户状态"
            virtual={false}
            allowClear
            options={[
              { value: true, label: "启用" },
              { value: false, label: "停用" },
            ]}
            onChange={(v) => setFilters((f) => ({ ...f, enabled: v, page: 1 }))}
          />
        </div>
        <LoadError error={remote.error} retry={remote.reload} />
        <Records<Account>
          data={remote.data}
          loading={remote.loading}
          page={filters.page}
          size={filters.size}
          onPage={(page, size) => setFilters((f) => ({ ...f, page, size }))}
          columns={[
            {
              title: "用户",
              render: (_, u) => (
                <div>
                  <strong>{u.displayName}</strong>
                  <small className="operation-sub">
                    {u.username}
                    {u.id === user?.id ? " · 当前账户" : ""}
                  </small>
                </div>
              ),
            },
            { title: "邮箱", render: (_, u) => u.email || "未填写" },
            { title: "角色", render: (_, u) => roles(u) },
            {
              title: "状态",
              render: (_, u) => (
                <Tag color={u.enabled ? "success" : "default"}>
                  {u.enabled ? "启用" : "停用"}
                </Tag>
              ),
            },
            { title: "创建时间", dataIndex: "createdAt", render: dateTime },
            {
              title: "最近登录",
              render: (_, u) =>
                u.lastLoginAt ? dateTime(u.lastLoginAt) : "暂无记录",
            },
            { title: "操作", width: 260, render: (_, u) => actions(u) },
          ]}
          card={(u) => (
            <>
              <div className="operation-card-title">
                <strong>{u.displayName}</strong>
                <Tag color={u.enabled ? "success" : "default"}>
                  {u.enabled ? "启用" : "停用"}
                </Tag>
              </div>
              <p>{u.username}</p>
              <p>{u.email || "未填写邮箱"}</p>
              {roles(u)}
              <div className="admin-card-actions">{actions(u)}</div>
            </>
          )}
        />
      </section>
      <DrawerPresence>{editor && (
        <UserEditor
          account={editor}
          selfId={user!.id}
          onClose={() => setEditor(undefined)}
          onSaved={() => {
            setEditor(undefined);
            setFilters((f) => ({ ...f, page: 1 }));
            remote.reload();
            void refreshProfile();
          }}
        />
      )}</DrawerPresence>
      <DrawerPresence>{selected && (
        <UserDetail id={selected} onClose={() => setSelected(undefined)} />
      )}</DrawerPresence>
      {passwordUser && (
        <PasswordReset
          account={passwordUser}
          onClose={() => setPasswordUser(undefined)}
        />
      )}
    </div>
  );
}
function UserEditor({
  account,
  selfId,
  onClose,
  onSaved,
}: {
  account: Account | "create";
  selfId: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isNew = account === "create",
    self = !isNew && account.id === selfId,
    [form] = Form.useForm<Values>(),
    { message } = App.useApp(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function save(v: Values) {
    setBusy(true);
    setError("");
    const profile = {
      displayName: v.displayName.trim(),
      email: v.email?.trim() || null,
      roles: v.roles,
      enabled: v.enabled,
    };
    try {
      await request(isNew ? "/api/users" : "/api/users/" + account.id, {
        method: isNew ? "POST" : "PUT",
        body: JSON.stringify(
          isNew
            ? {
                username: v.username,
                credentials: { password: v.password },
                profile,
              }
            : profile,
        ),
      });
      void message.success(
        isNew ? "用户已创建，可使用设置的密码登录" : "用户信息与角色已保存",
      );
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Drawer
      title={isNew ? "新增用户" : "编辑用户"}
      open
      width="min(600px,100vw)"
      onClose={onClose}
      maskClosable={!busy}
      closable={!busy}
      footer={
        <div className="drawer-actions">
          <Button disabled={busy} onClick={onClose}>
            取消
          </Button>
          <Button
            aria-label="保存用户"
            type="primary"
            loading={busy}
            onClick={() => form.submit()}
          >
            保存用户
          </Button>
        </div>
      }
    >
      {error && (
        <Alert className="form-alert" type="error" showIcon message={error} />
      )}
      <Form
        form={form}
        layout="vertical"
        onFinish={save}
        disabled={busy}
        initialValues={isNew ? { enabled: true, roles: ["STUDENT"] } : account}
        autoComplete="off"
        scrollToFirstError
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[
            { required: true, message: "请输入用户名" },
            {
              pattern: /^[A-Za-z0-9_.-]{3,64}$/,
              message: "使用 3–64 位字母、数字、点、横线或下划线",
            },
          ]}
          extra="用户名创建后保持不变，历史记录继续关联同一用户。"
        >
          <Input maxLength={64} disabled={!isNew} autoComplete="off" />
        </Form.Item>
        <Form.Item
          label="姓名"
          name="displayName"
          rules={[
            { required: true, whitespace: true, message: "请输入姓名" },
            { max: 64 },
          ]}
        >
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item
          label="邮箱"
          name="email"
          rules={[{ type: "email", message: "请输入有效邮箱" }, { max: 254 }]}
          extra="启用的采购员邮箱将加入库存通知收件人。"
        >
          <Input maxLength={254} autoComplete="off" />
        </Form.Item>
        <Form.Item
          label="所属角色"
          name="roles"
          rules={[
            {
              required: true,
              type: "array",
              min: 1,
              message: "至少选择一个角色",
            },
            {
              validator: async (_, v: string[]) => {
                if (self && !v?.includes("ADMIN"))
                  throw new Error("当前账户须保留系统管理员角色");
              },
            },
          ]}
        >
          <Select
            aria-label="分配用户角色"
            mode="multiple"
            virtual={false}
            options={Object.entries(roleNames).map(([value, label]) => ({
              value,
              label,
            }))}
          />
        </Form.Item>
        <Form.Item
          label="账户启用"
          name="enabled"
          valuePropName="checked"
          extra={self ? "当前管理员不能停用自身" : undefined}
        >
          <Switch
            disabled={self}
            checkedChildren="启用"
            unCheckedChildren="停用"
          />
        </Form.Item>
        {isNew && (
          <Form.Item
            label="初始密码"
            name="password"
            rules={passwordRules}
            extra="至少 12 个字符，UTF-8 不超过 72 字节；保存后不会再次返回密码。"
          >
            <Input.Password maxLength={72} autoComplete="new-password" />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  );
}
function UserDetail({ id, onClose }: { id: number; onClose: () => void }) {
  const d = useRemote<Account>("/api/users/" + id),
    u = d.data;
  return (
    <Drawer title="用户详情" open width="min(600px,100vw)" onClose={onClose}>
      <LoadError error={d.error} retry={d.reload} />
      {u && (
        <>
          <div className="admin-profile">
            <span>{u.displayName.slice(0, 1)}</span>
            <div>
              <h2>{u.displayName}</h2>
              <p>{u.username}</p>
            </div>
          </div>
          <Descriptions
            column={1}
            items={[
              { key: "email", label: "邮箱", children: u.email || "未填写" },
              { key: "roles", label: "角色", children: roles(u) },
              {
                key: "status",
                label: "状态",
                children: u.enabled ? "启用" : "停用",
              },
              {
                key: "created",
                label: "创建时间",
                children: dateTime(u.createdAt),
              },
              {
                key: "updated",
                label: "更新时间",
                children: dateTime(u.updatedAt),
              },
              {
                key: "login",
                label: "最近登录",
                children: u.lastLoginAt ? dateTime(u.lastLoginAt) : "暂无记录",
              },
            ]}
          />
        </>
      )}
    </Drawer>
  );
}
function PasswordReset({
  account,
  onClose,
}: {
  account: Account;
  onClose: () => void;
}) {
  const [form] = Form.useForm<{ password: string }>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    { user, logout } = useAuth(),
    { message } = App.useApp();
  async function save(v: { password: string }) {
    setBusy(true);
    setError("");
    try {
      await request("/api/users/" + account.id + "/password", {
        method: "PATCH",
        body: JSON.stringify(v),
      });
      void message.success("密码已重置，原有会话已撤销");
      onClose();
      if (account.id === user?.id) logout();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      open
      title={"重置密码 · " + account.displayName}
      onCancel={onClose}
      maskClosable={!busy}
      closable={!busy}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            取消
          </Button>
          <Button
            aria-label="确认重置密码"
            type="primary"
            loading={busy}
            onClick={() => form.submit()}
          >
            确认重置密码
          </Button>
        </Space>
      }
    >
      <p className="material-form-note">
        此操作会撤销该用户所有旧登录会话。
        {account.id === user?.id && "你将需要使用新密码重新登录。"}
      </p>
      {error && <Alert className="form-alert" type="error" message={error} />}
      <Form layout="vertical" form={form} onFinish={save}>
        <Form.Item name="password" label="新密码" rules={passwordRules}>
          <Input.Password maxLength={72} autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
