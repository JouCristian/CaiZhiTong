import { useEffect, useRef, useState } from 'react'
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
} from 'antd'
import Decimal from 'decimal.js'
import { ApiError, request, newRequestId } from '../api'
import { useAuth } from '../auth'
import type { MaterialPage } from '../materials/types'
import { money, quantity } from '../materials/types'
import type { Batch, Page } from '../stock/types'
import { query } from '../stock/types'
import type { IssueDetail, Quota } from './types'
Decimal.set({ precision: 50 })
type Values = {
  quantity: string
  course?: string
  projectNo: string
  usage: string
  remark?: string
}
type Pending = { key: string; values: Values; batch: Batch }
function restored(key: string): Pending | undefined {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return
    const p = JSON.parse(raw)
    if (typeof p.key === 'string' && p.values && p.batch?.id) return p
  } catch {
    /* no valid pending request */
  }
}
export function MaterialSearch({
  value,
  onChange,
  includeInactive = false,
}: {
  includeInactive?: boolean
  value?: number
  onChange?: (id: number) => void
}) {
  const [term, setTerm] = useState(''),
    [options, setOptions] = useState<{ value: number; label: string }[]>([]),
    [loading, setLoading] = useState(false),
    [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const timer = window.setTimeout(() => {
      setLoading(true)
      setError('')
      request<MaterialPage>(
        '/api/materials' + query({ keyword: term, status: includeInactive ? undefined : 'ACTIVE', size: 30 }),
      )
        .then((p) => {
          if (active)
            setOptions(
              p.records.map((m) => ({
                value: m.id,
                label: m.name + ' · ' + m.materialCode,
              })),
            )
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
  }, [term, includeInactive])
  return (
    <Select
      aria-label="搜索领用材料"
      value={value}
      showSearch
      filterOption={false}
      virtual={false}
      getPopupContainer={(t) => t.parentElement!}
      onSearch={setTerm}
      onChange={onChange}
      options={options}
      loading={loading}
      placeholder="输入材料名称或编码"
      notFoundContent={error || '暂无可选材料'}
    />
  )
}
export function IssueCreator({
  code,
  onClose,
  onSaved,
}: {
  code?: string
  onClose: () => void
  onSaved: (d: IssueDetail) => void
}) {
  const { user } = useAuth(),
    { message } = App.useApp(),
    storageKey = 'caizhitong.pending-issue:' + user!.id
  const [cached] = useState(() => restored(storageKey)),
    [form] = Form.useForm<Values>(),
    [materialId, setMaterialId] = useState<number | undefined>(
      cached?.batch.itemId,
    ),
    [batch, setBatch] = useState<Batch | undefined>(cached?.batch),
    [batches, setBatches] = useState<Batch[]>([]),
    [total, setTotal] = useState(0),
    [batchPage, setBatchPage] = useState(1),
    [quota, setQuota] = useState<Quota>(),
    [loading, setLoading] = useState(!!code),
    [error, setError] = useState(
      cached ? '存在尚未确认的领用请求，请安全重试以核对结果。' : '',
    ),
    [saving, setSaving] = useState(false),
    [confirm, setConfirm] = useState<Values | undefined>(cached?.values),
    [attempted, setAttempted] = useState(!!cached),
    [canEdit, setCanEdit] = useState(false),
    [revision, setRevision] = useState(0)
  const key = useRef(cached?.key || newRequestId()),
    busy = useRef(false),
    inputQuantity = Form.useWatch('quantity', form) || '0'
  useEffect(() => {
    if (!code || cached) return
    let active = true
    setLoading(true)
    request<Batch>('/api/stock-batches/' + encodeURIComponent(code))
      .then((b) => {
        if (active) {
          setBatch(b)
          setMaterialId(b.itemId)
        }
      })
      .catch((e) => {
        if (active) setError((e as Error).message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [code, cached, revision])
  useEffect(() => {
    if (!materialId || attempted) return
    let active = true
    setLoading(true)
    setError('')
    Promise.all([
      request<Page<Batch>>(
        '/api/materials/' +
          materialId +
          '/issue-batches' +
          query({ page: batchPage, size: 50 }),
      ),
      request<Quota>('/api/materials/' + materialId + '/issue-quota'),
      batch
        ? request<Batch>('/api/stock-batches/' + batch.batchCode)
        : Promise.resolve(undefined),
    ])
      .then(([p, q, fresh]) => {
        if (active) {
          setBatches((prev) =>
            batchPage === 1 ? p.records : [...prev, ...p.records],
          )
          setTotal(p.total)
          setQuota(q)
          if (fresh) setBatch(fresh)
        }
      })
      .catch((e) => {
        if (active) setError((e as Error).message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [materialId, batchPage, revision, attempted])
  const currentOptions =
    batch && !batches.some((b) => b.id === batch.id)
      ? [batch, ...batches]
      : batches
  const amount = (() => {
    try {
      return new Decimal(confirm?.quantity || inputQuantity || 0)
        .mul(batch?.unitPriceSnapshot || 0)
        .toFixed()
    } catch {
      return '0'
    }
  })()
  async function submit(values: Values) {
    if (!batch || busy.current) return
    busy.current = true
    setSaving(true)
    setError('')
    setAttempted(true)
    sessionStorage.setItem(
      storageKey,
      JSON.stringify({ key: key.current, values, batch }),
    )
    try {
      const d = await request<IssueDetail>('/api/issues', {
        method: 'POST',
        body: JSON.stringify({
          idempotencyKey: key.current,
          projectNo: values.projectNo,
          usage: values.usage,
          course: values.course,
          remark: values.remark,
          lines: [{ batchId: batch.id, quantity: values.quantity }],
        }),
      })
      sessionStorage.removeItem(storageKey)
      void message.success('领用成功，库存与个人额度已同步')
      onSaved(d)
    } catch (e) {
      setError((e as Error).message)
      setCanEdit(e instanceof ApiError && [400, 404, 409].includes(e.status))
    } finally {
      busy.current = false
      setSaving(false)
    }
  }
  const unavailable =
    !!batch &&
    (batch.materialStatus !== 'ACTIVE' || new Decimal(batch.quantity).lte(0))
  function editAgain() {
    sessionStorage.removeItem(storageKey)
    key.current = newRequestId()
    setAttempted(false)
    setConfirm(undefined)
    setError('')
    setCanEdit(false)
    setRevision((r) => r + 1)
  }
  return (
    <Drawer
      title={code ? '领用此批次' : '新建领用单'}
      open
      width="min(650px,100vw)"
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
              {canEdit && <Button onClick={editAgain}>修改后重新提交</Button>}
              <Button
                type="primary"
                loading={saving}
                onClick={() => confirm && void submit(confirm)}
              >
                安全重试原请求
              </Button>
            </>
          ) : confirm ? (
            <>
              <Button disabled={saving} onClick={() => setConfirm(undefined)}>
                返回修改
              </Button>
              <Button
                type="primary"
                loading={saving}
                onClick={() => void submit(confirm)}
              >
                确认领用
              </Button>
            </>
          ) : (
            <Button
              type="primary"
              disabled={!batch || unavailable || loading || !!error}
              onClick={() => form.submit()}
            >
              核对领用信息
            </Button>
          )}
        </div>
      }
    >
      {error && (
        <Alert
          className="form-alert"
          showIcon
          type="error"
          message={attempted ? '领用未确认成功' : '无法准备领用'}
          description={error}
          action={
            !attempted ? (
              <Button onClick={() => setRevision((r) => r + 1)}>重试</Button>
            ) : undefined
          }
        />
      )}
      {loading && <Skeleton active paragraph={{ rows: 2 }} />}
      {confirm && batch && (
        <section className="issue-batch-summary">
          <div>
            <strong>{batch.itemName}</strong>
            <span>{batch.batchCode}</span>
            <small>{batch.spec || '未填写规格'}</small>
          </div>
          <div className="issue-available">
            <span>本次领用</span>
            <strong>
              {quantity(confirm.quantity)} <small>{batch.unit}</small>
            </strong>
          </div>
        </section>
      )}
      <Form
        form={form}
        layout="vertical"
        initialValues={cached?.values || { quantity: '1' }}
        onFinish={setConfirm}
        disabled={saving || !!confirm || attempted}
        scrollToFirstError
        style={{ display: confirm ? 'none' : undefined }}
      >
        {!code && !cached && (
          <Form.Item label="材料" required>
            <MaterialSearch
              value={materialId}
              onChange={(id) => {
                setMaterialId(id)
                setBatch(undefined)
                setBatches([])
                setBatchPage(1)
                setQuota(undefined)
              }}
            />
          </Form.Item>
        )}
        {!code && !cached && (
          <Form.Item label="库存批次" required>
            <Select
              aria-label="选择领用批次"
              value={batch?.id}
              virtual={false}
              getPopupContainer={(t) => t.parentElement!}
              placeholder="仅显示仍有余额的批次"
              disabled={!materialId || loading || !!confirm || attempted}
              onChange={(id) =>
                setBatch(currentOptions.find((b) => b.id === id))
              }
              options={currentOptions
                .filter((b) => new Decimal(b.quantity).gt(0))
                .map((b) => ({
                  value: b.id,
                  label:
                    b.batchCode +
                    ' · ' +
                    quantity(b.quantity) +
                    ' ' +
                    b.unit +
                    (b.location ? ' · ' + b.location : ''),
                }))}
              notFoundContent="暂无可领用批次"
            />
            {batches.length < total && (
              <Button type="link" onClick={() => setBatchPage((p) => p + 1)}>
                加载更多批次
              </Button>
            )}
          </Form.Item>
        )}
        {batch && (
          <section className="issue-batch-summary">
            <div>
              <strong>{batch.itemName}</strong>
              <span>{batch.batchCode}</span>
              <small>{batch.spec || '未填写规格'}</small>
            </div>
            <div className="issue-available">
              <span>{attempted ? '提交前可用' : '当前可用'}</span>
              <strong>
                {quantity(batch.quantity)} <small>{batch.unit}</small>
              </strong>
            </div>
          </section>
        )}
        {unavailable && !attempted && (
          <Alert
            type="warning"
            showIcon
            message="该材料已停用、归档或批次已无库存，请选择其他批次。"
          />
        )}
        {quota && (
          <div className="issue-quota" role="status">
            <strong>{quota.monthStart.slice(0, 7)} · 个人用量</strong>
            <p>
              本月已领 {quantity(quota.usedQuantity)} {batch?.unit || ''}
              {quota.monthlyIssueLimit != null ? (
                <>
                  {' '}
                  / 限额 {quantity(quota.monthlyIssueLimit)} · 剩余{' '}
                  <b>{quantity(quota.remainingQuantity || '0')}</b>
                </>
              ) : (
                ' · 未配置限额'
              )}
            </p>
            <small>所有批次共享额度 · {quota.businessZone}</small>
          </div>
        )}
        <Form.Item
          label="本次领用数量"
          name="quantity"
          rules={[
            { required: true, message: '请输入领用数量' },
            {
              validator: async (_, v) => {
                if (!v) return
                const n = new Decimal(v)
                if (n.lte(0)) throw new Error('领用数量必须大于零')
                if (batch && n.gt(batch.quantity))
                  throw new Error('领用数量超过当前库存')
                if (
                  quota?.remainingQuantity != null &&
                  n.gt(quota.remainingQuantity)
                )
                  throw new Error('个人本月剩余额度不足')
              },
            },
          ]}
        >
          <InputNumber
            stringMode
            min="0.001"
            max="999999999999999.999"
            precision={3}
            addonAfter={batch?.unit || '单位'}
            style={{ width: '100%' }}
          />
        </Form.Item>
        <Form.Item
          label="项目编号"
          name="projectNo"
          rules={[
            { required: true, whitespace: true, message: '请填写项目编号' },
            { max: 100 },
          ]}
        >
          <Input placeholder="例如：LAB-2026-001" maxLength={100} />
        </Form.Item>
        <Form.Item label="课程（选填）" name="course" rules={[{ max: 100 }]}><Input maxLength={100} placeholder="例如：模拟电子技术实验" /></Form.Item>
        <Form.Item
          label="用途"
          name="usage"
          rules={[
            { required: true, whitespace: true, message: '请填写领用用途' },
            { max: 500 },
          ]}
        >
          <Input.TextArea
            rows={3}
            maxLength={500}
            placeholder="说明本次领用的实验或使用目的"
          />
        </Form.Item>
        <Form.Item label="备注" name="remark" rules={[{ max: 2000 }]}>
          <Input.TextArea rows={2} maxLength={2000} placeholder="选填" />
        </Form.Item>
      </Form>
      {batch && (
        <section className="issue-amount">
          <span>本次领用金额</span>
          <strong>{money(amount, batch.currency)}</strong>
          <small>
            按批次入库单价 {money(batch.unitPriceSnapshot, batch.currency)} /{' '}
            {batch.unit} 计价
          </small>
        </section>
      )}
      {confirm && (
        <section className="issue-confirm">
          <Alert
            type="info"
            showIcon
            message="请核对领用信息，确认后立即扣减库存与额度。"
          />
          <Descriptions
            column={1}
            size="small"
            items={[
              { key: 'user', label: '领用人', children: user?.displayName },
              {
                key: 'qty',
                label: '数量',
                children: confirm.quantity + ' ' + batch?.unit,
              },
              { key: 'project', label: '项目', children: confirm.projectNo },
              { key: 'usage', label: '用途', children: confirm.usage },
              { key: 'course', label: '课程', children: confirm.course || '未关联课程' },
            ]}
          />
        </section>
      )}
    </Drawer>
  )
}
