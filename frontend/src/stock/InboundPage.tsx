import { useEffect, useRef, useState } from 'react'
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
} from 'antd'
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import Decimal from 'decimal.js'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { useSearchParams } from 'react-router-dom'
import { request, ApiError, newRequestId } from '../api'
import { useAuth } from '../auth'
import { dateTime, money, quantity } from '../materials/types'
import type { Material, MaterialPage } from '../materials/types'
import { BatchList } from './BatchViews'
import { query } from './types'
import type { Page, Order, OrderDetail } from './types'
import './stock.css'
Decimal.set({ precision: 50 })
type Line = {
  itemId?: number
  quantity?: string
  unitPrice?: string
  currency?: string
  unit?: string
  location?: string
  expireDate?: Dayjs
  remark?: string
}
type InputValues = { remark?: string; lines: Line[] }
const PENDING_KEY = 'caizhitong.pending-inbound'
function restorePending(
  storageKey: string,
): { key: string; values: InputValues } | undefined {
  try {
    const raw = sessionStorage.getItem(storageKey)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (typeof saved.key !== 'string' || !Array.isArray(saved.values?.lines))
      return
    return {
      key: saved.key,
      values: {
        ...saved.values,
        lines: saved.values.lines.map(
          (l: Omit<Line, 'expireDate'> & { expireDate?: string }) => ({
            ...l,
            expireDate: l.expireDate ? dayjs(l.expireDate) : undefined,
          }),
        ),
      },
    }
  } catch {
    return
  }
}
function MaterialSelect({
  value,
  onChange,
  onSelected,
  disabled,
}: {
  value?: number
  onChange?: (value: number) => void
  onSelected: (m: Material) => void
  disabled?: boolean
}) {
  const [rows, setRows] = useState<Material[]>([]),
    [term, setTerm] = useState(''),
    [loading, setLoading] = useState(false),
    [error, setError] = useState('')
  const known = useRef(new Map<number, Material>())
  useEffect(() => {
    let active = true
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError('')
      request<MaterialPage>(
        '/api/materials' +
          query({ keyword: term, status: 'ACTIVE', page: 1, size: 30 }),
      )
        .then((p) => {
          if (active) {
            p.records.forEach((m) => known.current.set(m.id, m))
            setRows(p.records)
          }
        })
        .catch((e) => {
          if (active) setError((e as Error).message)
        })
        .finally(() => {
          if (active) setLoading(false)
        })
    }, 250)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [term])
  const selected = value ? known.current.get(value) : undefined
  const options =
    selected && !rows.some((m) => m.id === value) ? [selected, ...rows] : rows
  return (
    <Select
      aria-label="选择入库材料"
      styles={{ popup: { root: { zIndex: 1200 } } }}
      getPopupContainer={(trigger) => trigger.parentElement!}
      virtual={false}
      showSearch
      disabled={disabled}
      placeholder="搜索启用材料的名称或编码"
      filterOption={false}
      loading={loading}
      value={value}
      onSearch={setTerm}
      notFoundContent={loading ? '正在查询…' : error || '没有可入库材料'}
      options={options.map((m) => ({
        value: m.id,
        label: m.name + ' · ' + m.materialCode,
      }))}
      onChange={(id) => {
        onChange?.(id)
        const m = known.current.get(id)
        if (m) onSelected(m)
      }}
    />
  )
}
function InboundCreator({
  onClose,
  onSaved,
}: {
  onClose: () => void
  onSaved: (d: OrderDetail) => void
}) {
  const { user } = useAuth()
  const pendingKey = PENDING_KEY + ':' + user!.id
  const [restored] = useState(() => restorePending(pendingKey))
  const [form] = Form.useForm<InputValues>(),
    lines = Form.useWatch('lines', form) || [],
    [materials, setMaterials] = useState<Record<number, Material>>({}),
    [saving, setSaving] = useState(false),
    [error, setError] = useState(
      restored ? '发现尚未确认的入库请求，请安全重试以核对结果。' : '',
    ),
    [attempted, setAttempted] = useState(!!restored),
    [canEdit, setCanEdit] = useState(false)
  const key = useRef(restored?.key || newRequestId()),
    busy = useRef(false)
  const [pending, setPending] = useState<InputValues | undefined>(
    restored?.values,
  )
  const { message } = App.useApp()
  // A failed/unknown response keeps the exact payload and key available for safe replay.
  async function submit(values: InputValues) {
    if (busy.current) return
    busy.current = true
    setSaving(true)
    setError('')
    setAttempted(true)
    setPending(values)
    sessionStorage.setItem(
      pendingKey,
      JSON.stringify({ key: key.current, values }),
    )
    try {
      const detail = await request<OrderDetail>('/api/inbound-orders', {
        method: 'POST',
        body: JSON.stringify({
          idempotencyKey: key.current,
          remark: values.remark,
          lines: values.lines.map((l) => ({
            ...l,
            expireDate: l.expireDate?.format('YYYY-MM-DD'),
          })),
        }),
      })
      sessionStorage.removeItem(pendingKey)
      void message.success('入库成功，库存与流水已同步')
      onSaved(detail)
    } catch (e) {
      setError((e as Error).message)
      setCanEdit(e instanceof ApiError && [400, 404, 409].includes(e.status))
    } finally {
      busy.current = false
      setSaving(false)
    }
  }
  const totals: Record<string, Decimal> = {}
  const preview = lines.map((l) => {
    const m = l.itemId ? materials[l.itemId] : undefined
    let amount = new Decimal(0)
    try {
      amount = new Decimal(l.quantity || 0).mul(l.unitPrice || 0)
    } catch {
      /* incomplete input */
    }
    const currency = l.currency || m?.currency || 'CNY'
    totals[currency] = (totals[currency] || new Decimal(0)).add(amount)
    return {
      name: m?.name || (l.itemId ? '材料 #' + l.itemId : '尚未选择材料'),
      qty: l.quantity || '0',
      unit: l.unit || '',
      price: l.unitPrice || '0',
      currency,
      amount: amount.toFixed(),
    }
  })
  return (
    <Drawer
      title="新建入库单"
      open
      width="min(1000px,100vw)"
      onClose={onClose}
      maskClosable={!saving && !attempted}
      keyboard={!saving}
      closable={!saving}
      footer={
        <div className="drawer-actions">
          <Button disabled={saving} onClick={onClose}>
            关闭
          </Button>
          {attempted && error ? (
            <>
              {canEdit && (
                <Button
                  disabled={saving}
                  onClick={() => {
                    sessionStorage.removeItem(pendingKey)
                    key.current = newRequestId()
                    setAttempted(false)
                    setPending(undefined)
                    setError('')
                    setCanEdit(false)
                  }}
                >
                  修改后重新提交
                </Button>
              )}
              <Button
                type="primary"
                loading={saving}
                onClick={() => pending && void submit(pending)}
              >
                安全重试原请求
              </Button>
            </>
          ) : (
            <Button
              type="primary"
              loading={saving}
              onClick={() => form.submit()}
            >
              确认入库
            </Button>
          )}
        </div>
      }
    >
      <Alert
        type="info"
        showIcon
        message="提交后生成正式入库单、批次与流水，历史记录不可直接编辑。"
      />
      {error && (
        <Alert
          className="form-alert"
          type="error"
          showIcon
          message="入库未确认成功"
          description={
            error + '。网络异常时请优先安全重试原请求，避免重复入库。'
          }
        />
      )}
      <Form
        form={form}
        layout="vertical"
        initialValues={restored?.values || { lines: [{ quantity: '1' }] }}
        onFinish={submit}
        disabled={saving || attempted}
        scrollToFirstError
      >
        <Form.List
          name="lines"
          rules={[
            {
              validator: async (_, v) => {
                if (!v?.length) throw new Error('至少添加一条材料明细')
              },
            },
          ]}
        >
          {(fields, { add, remove }, { errors }) => (
            <>
              {fields.map((field, index) => (
                <section className="inbound-line" key={field.key}>
                  <div className="inbound-line-top">
                    <h3>明细 {index + 1}</h3>
                    <Button
                      type="text"
                      danger
                      disabled={saving || attempted || fields.length === 1}
                      aria-label={'删除明细 ' + (index + 1)}
                      icon={<DeleteOutlined />}
                      onClick={() => remove(field.name)}
                    />
                  </div>
                  <Form.Item
                    label="材料"
                    name={[field.name, 'itemId']}
                    rules={[{ required: true, message: '请选择启用的材料' }]}
                  >
                    <MaterialSelect
                      disabled={saving || attempted}
                      onSelected={(m) => {
                        setMaterials((old) => ({ ...old, [m.id]: m }))
                        form.setFieldValue(
                          ['lines', field.name, 'unitPrice'],
                          m.unitPrice,
                        )
                        form.setFieldValue(
                          ['lines', field.name, 'currency'],
                          m.currency,
                        )
                        form.setFieldValue(
                          ['lines', field.name, 'unit'],
                          m.unit,
                        )
                      }}
                    />
                  </Form.Item>
                  <Form.Item hidden name={[field.name, 'currency']}>
                    <Input />
                  </Form.Item>
                  <Form.Item hidden name={[field.name, 'unit']}>
                    <Input />
                  </Form.Item>
                  <div className="inbound-grid">
                    <Form.Item
                      label="入库数量"
                      name={[field.name, 'quantity']}
                      rules={[
                        { required: true, message: '请输入数量' },
                        {
                          validator: async (_, v) => {
                            if (v && new Decimal(v).lte(0))
                              throw new Error('数量必须大于零')
                          },
                        },
                      ]}
                    >
                      <InputNumber
                        aria-label={'入库数量 ' + (index + 1)}
                        stringMode
                        min="0.001"
                        max="999999999999999.999"
                        precision={3}
                        style={{ width: '100%' }}
                        addonAfter={lines[field.name]?.unit || '单位'}
                      />
                    </Form.Item>
                    <Form.Item
                      label="入库单价"
                      name={[field.name, 'unitPrice']}
                      rules={[{ required: true, message: '请输入单价' }]}
                    >
                      <InputNumber
                        aria-label={'入库单价 ' + (index + 1)}
                        stringMode
                        min="0"
                        max="9999999999.9999"
                        precision={4}
                        style={{ width: '100%' }}
                        addonAfter={lines[field.name]?.currency || 'CNY'}
                      />
                    </Form.Item>
                    <Form.Item
                      label="库位"
                      name={[field.name, 'location']}
                      rules={[{ max: 100 }]}
                    >
                      <Input
                        aria-label={'库位 ' + (index + 1)}
                        placeholder="例如 A-01"
                        maxLength={100}
                      />
                    </Form.Item>
                    <Form.Item
                      label="有效期（可选）"
                      name={[field.name, 'expireDate']}
                    >
                      <DatePicker
                        aria-label={'有效期 ' + (index + 1)}
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                    <Form.Item
                      label="明细备注"
                      name={[field.name, 'remark']}
                      rules={[{ max: 500 }]}
                    >
                      <Input
                        aria-label={'明细备注 ' + (index + 1)}
                        maxLength={500}
                      />
                    </Form.Item>
                  </div>
                </section>
              ))}
              <Form.ErrorList errors={errors} />
              <Button
                block
                type="dashed"
                icon={<PlusOutlined />}
                disabled={saving || attempted || fields.length >= 50}
                onClick={() => add({ quantity: '1' })}
              >
                添加材料明细
              </Button>
            </>
          )}
        </Form.List>
        <Form.Item
          label="入库单备注"
          name="remark"
          rules={[{ max: 1000 }]}
          style={{ marginTop: 24 }}
        >
          <Input.TextArea
            rows={2}
            maxLength={1000}
            placeholder="填写来源、用途或其他说明"
          />
        </Form.Item>
      </Form>
      <section className="inbound-summary">
        <h3>入库确认</h3>
        {preview.map((p, i) => (
          <div className="inbound-summary-row" key={i}>
            <span>{p.name}</span>
            <span>
              {quantity(p.qty)} {p.unit} × {money(p.price, p.currency)} ={' '}
              {money(p.amount, p.currency)}
            </span>
          </div>
        ))}
        <div className="inbound-summary-total">
          {Object.entries(totals).map(([c, v]) => (
            <span key={c}>合计 {money(v.toFixed(), c)}</span>
          ))}
        </div>
      </section>
    </Drawer>
  )
}
export function OrderDrawer({
  id,
  onClose,
}: {
  id: number
  onClose: () => void
}) {
  const [data, setData] = useState<OrderDetail>(),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setError('')
    request<OrderDetail>('/api/inbound-orders/' + id)
      .then((d) => {
        if (active) setData(d)
      })
      .catch((e) => {
        if (active) setError((e as Error).message)
      })
    return () => {
      active = false
    }
  }, [id, retry])
  return (
    <Drawer title="入库单详情" open width="min(1100px,100vw)" onClose={onClose}>
      {error ? (
        <Alert
          type="error"
          message={error}
          action={<Button onClick={() => setRetry((r) => r + 1)}>重试</Button>}
        />
      ) : data ? (
        <>
          <div className="stock-detail-title">
            <div>
              <h2>{data.order.orderNo}</h2>
              <p>已完成 · {data.lines.length} 条材料明细</p>
            </div>
            <Tag color="success">已入库</Tag>
          </div>
          <Descriptions
            column={{ xs: 1, sm: 2 }}
            items={[
              {
                key: 'operator',
                label: '经办人',
                children: data.order.operatorName,
              },
              {
                key: 'date',
                label: '入库时间',
                children: dateTime(data.order.receivedAt),
              },
              {
                key: 'created',
                label: '创建时间',
                children: dateTime(data.order.createdAt),
              },
              {
                key: 'remark',
                label: '备注',
                children: data.order.remark || '未填写',
              },
            ]}
          />
          <h3>入库明细与批次</h3>
          <BatchList data={data.lines} original />
          <div className="inbound-summary-total">
            {data.totals.map((t) => (
              <span key={t.currency}>总金额 {money(t.amount, t.currency)}</span>
            ))}
          </div>
          <p className="stock-hint">
            数量、名称、规格、单位及价格均保留入库时快照。点击批次号可查看二维码标签。
          </p>
        </>
      ) : (
        <Skeleton active />
      )}
    </Drawer>
  )
}
export function InboundPage() {
  const { user } = useAuth(),
    [params, setParams] = useSearchParams(),
    [filters, setFilters] = useState({
      page: 1,
      size: 20,
      keyword: '',
      from: undefined as string | undefined,
      to: undefined as string | undefined,
    }),
    [search, setSearch] = useState(''),
    [data, setData] = useState<Page<Order>>({
      records: [],
      total: 0,
      page: 1,
      size: 20,
    }),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [revision, setRevision] = useState(0),
    [creating, setCreating] = useState(false)
  const orderParam = params.get('order'),
    selected =
      orderParam && /^\d+$/.test(orderParam) ? Number(orderParam) : undefined
  const canWrite = user?.roles.some((r) => r === 'ADMIN' || r === 'LAB_TECH')
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    request<Page<Order>>('/api/inbound-orders' + query(filters))
      .then((d) => {
        if (active) setData(d)
      })
      .catch((e) => {
        if (active) {
          setError((e as Error).message)
          setData((d) => ({ ...d, records: [], total: 0 }))
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [filters, revision])
  return (
    <div className="page-enter stock-page">
      <div className="page-heading">
        <div>
          <h1>入库管理</h1>
          <p>一次确认，让材料、批次与库存同步有序。</p>
        </div>
        <Space>
          <Button
            aria-label="刷新入库单"
            icon={<ReloadOutlined />}
            onClick={() => setRevision((r) => r + 1)}
          />
          {canWrite && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreating(true)}
            >
              新建入库单
            </Button>
          )}
        </Space>
      </div>
      <section className="surface stock-table-panel">
        <div className="stock-filters" style={{ marginTop: 24 }}>
          <Input.Search
            aria-label="搜索入库单号"
            placeholder="按入库单号查询"
            value={search}
            allowClear
            onChange={(e) => {
              setSearch(e.target.value)
              if (!e.target.value)
                setFilters((f) => ({ ...f, page: 1, keyword: '' }))
            }}
            onSearch={() =>
              setFilters((f) => ({ ...f, page: 1, keyword: search.trim() }))
            }
          />
          <DatePicker.RangePicker
            aria-label="入库时间范围"
            onChange={(dates) =>
              setFilters((f) => ({
                ...f,
                page: 1,
                from: dates?.[0]?.format('YYYY-MM-DD'),
                to: dates?.[1]?.format('YYYY-MM-DD'),
              }))
            }
          />
        </div>
        {error && (
          <Alert
            type="error"
            message="入库历史查询失败"
            description={error}
            action={
              <Button onClick={() => setRevision((r) => r + 1)}>重试</Button>
            }
          />
        )}
        <Table<Order>
          rowKey="id"
          loading={loading}
          dataSource={data.records}
          size="middle"
          scroll={{ x: 950 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无匹配的入库单"
              />
            ),
          }}
          columns={[
            {
              title: '入库单号',
              render: (_, r) => (
                <Button
                  type="link"
                  onClick={() => setParams({ order: String(r.id) })}
                >
                  {r.orderNo}
                </Button>
              ),
            },
            { title: '材料明细', render: (_, r) => r.lineCount + ' 条' },
            { title: '状态', render: () => <Tag color="success">已完成</Tag> },
            { title: '经办人', dataIndex: 'operatorName' },
            { title: '入库时间', render: (_, r) => dateTime(r.receivedAt) },
            { title: '备注', dataIndex: 'remark', ellipsis: true },
            {
              title: '操作',
              render: (_, r) => (
                <Button
                  type="link"
                  onClick={() => setParams({ order: String(r.id) })}
                >
                  查看详情
                </Button>
              ),
            },
          ]}
          pagination={{
            current: filters.page,
            pageSize: filters.size,
            total: data.total,
            showSizeChanger: true,
            showTotal: (t) => '共 ' + t + ' 份入库单',
            onChange: (page, size) =>
              setFilters((f) => ({
                ...f,
                page: size === f.size ? page : 1,
                size,
              })),
          }}
        />
        <p className="stock-hint">
          已提交单据保留完整历史。本轮不支持撤销或直接修改库存。
        </p>
      </section>
      {creating && (
        <InboundCreator
          onClose={() => setCreating(false)}
          onSaved={(d) => {
            setCreating(false)
            setRevision((r) => r + 1)
            setFilters((f) => ({
              ...f,
              page: 1,
              keyword: '',
              from: undefined,
              to: undefined,
            }))
            setSearch('')
            setParams({ order: String(d.order.id) })
          }}
        />
      )}
      {selected !== undefined && (
        <OrderDrawer id={selected} onClose={() => setParams({})} />
      )}
    </div>
  )
}
