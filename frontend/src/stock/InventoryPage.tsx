import { DrawerPresence } from '../motion/MotionDrawer'
import { useAuth } from '../auth'
import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  DatePicker,
  Empty,
  Input,
  Select,
  Skeleton,
  Table,
  Tabs,
  Tag,
  TreeSelect,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { Link, useSearchParams } from 'react-router-dom'
import { request } from '../api'
import type { Category } from '../materials/types'
import { categoryOptions, dateTime, money, quantity } from '../materials/types'
import { StockDrawer } from './BatchViews'
import { query, stockNames, txnNames } from './types'
import type { Page, Stock, Summary, Transaction } from './types'
import './stock.css'
type Filters = {
  page: number
  size: number
  keyword?: string
  categoryId?: number
  stockStatus?: string
  batchCode?: string
  txnType?: string
  from?: string
  to?: string
}
export function InventoryPage() {
  const { user } = useAuth()
  const canViewLedger = user?.roles.some(r => ['ADMIN', 'LAB_TECH', 'TEACHER', 'PURCHASER'].includes(r)) ?? false
  const [params] = useSearchParams()
  const [view, setView] = useState(
      params.get('view') === 'transactions' && canViewLedger ? 'transactions' : 'stocks',
    ),
    [filters, setFilters] = useState<Filters>({
      page: 1,
      size: 20,
      batchCode: params.get('batch') || undefined,
      keyword: params.get('keyword') || undefined,
    }),
    [search, setSearch] = useState(''),
    [revision, setRevision] = useState(0)
  const [summary, setSummary] = useState<Summary>(),
    [categories, setCategories] = useState<Category[]>([]),
    [data, setData] = useState<Page<Stock | Transaction>>({
      records: [],
      total: 0,
      page: 1,
      size: 20,
    }),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [summaryError, setSummaryError] = useState(''),
    [selected, setSelected] = useState<number>()
  const filter = (v: Partial<Filters>) =>
    setFilters((f) => ({ ...f, ...v, page: 1 }))
  useEffect(() => {
    let active = true
    setSummaryError('')
    Promise.all([
      request<Summary>('/api/stocks/summary'),
      request<Category[]>('/api/material-categories/tree'),
    ])
      .then(([s, c]) => {
        if (active) {
          setSummary(s)
          setCategories(c)
        }
      })
      .catch((e) => {
        if (active) setSummaryError((e as Error).message)
      })
    return () => {
      active = false
    }
  }, [revision])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    request<Page<Stock | Transaction>>(
      (view === 'stocks' ? '/api/stocks' : '/api/stock-transactions') +
        query(filters),
    )
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
  }, [filters, view, revision])
  return (
    <div className="page-enter stock-page">
      <div className="page-heading">
        <div>
          <h1>库存管理</h1>
          <p>从库存全貌，到每一个可追溯的批次。</p>
        </div>
        <Button
          aria-label="刷新库存"
          icon={<ReloadOutlined />}
          onClick={() => setRevision((r) => r + 1)}
        >
          刷新
        </Button>
      </div>
      {summaryError && (
        <Alert
          type="error"
          showIcon
          message="库存概览加载失败"
          description={summaryError}
          action={
            <Button onClick={() => setRevision((r) => r + 1)}>重试</Button>
          }
        />
      )}
      <section className="metrics-panel stock-metrics">
        {[
          { label: '材料种类', value: summary?.materialCount },
          { label: '有库存材料', value: summary?.stockedMaterialCount },
          { label: '低于安全库存', value: summary?.lowStockCount },
        ].map((m) => (
          <div className="metric" key={m.label}>
            <div className="metric-label">{m.label}</div>
            {summary ? (
              <div className="metric-value">
                {m.value}
                <span>种</span>
              </div>
            ) : (
              <Skeleton.Input active />
            )}
          </div>
        ))}
        <div className="metric">
          <div className="metric-label">库存总价值</div>
          {summary ? (
            <div className="stock-values">
              {summary.values.length ? (
                summary.values.map((v) => (
                  <strong key={v.currency}>
                    {money(v.amount, v.currency)}
                  </strong>
                ))
              ) : (
                <strong>CNY 0</strong>
              )}
            </div>
          ) : (
            <Skeleton.Input active />
          )}
          <span className="metric-note">按入库价格快照 · 分币种汇总</span>
        </div>
      </section>
      <section className="surface stock-table-panel">
        <Tabs
          activeKey={view}
          onChange={(v) => {
            setData({ records: [], total: 0, page: 1, size: 20 })
            setLoading(true)
            setView(v)
            setFilters({ page: 1, size: 20 })
            setSearch('')
          }}
          items={[
            { key: 'stocks', label: '材料库存' },
            ...(canViewLedger ? [{ key: 'transactions', label: '库存流水' }] : []),
          ]}
        />
        <div className="stock-filters">
          <Input.Search
            aria-label="搜索库存名称或编码"
            placeholder={
              view === 'stocks'
                ? '材料名称 / 编码'
                : '材料名称 / 编码 / 交易编号'
            }
            allowClear
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              if (!e.target.value) filter({ keyword: undefined })
            }}
            onSearch={() => filter({ keyword: search.trim() || undefined })}
          />
          {view === 'stocks' ? (
            <>
              <TreeSelect
                aria-label="库存分类"
                placeholder="全部分类"
                allowClear
                treeDefaultExpandAll
                treeData={categoryOptions(categories)}
                value={filters.categoryId}
                onChange={(categoryId) => filter({ categoryId })}
              />
              <Select
                aria-label="库存状态"
                placeholder="全部库存状态"
                allowClear
                value={filters.stockStatus}
                onChange={(stockStatus) => filter({ stockStatus })}
                options={Object.entries(stockNames).map(([value, label]) => ({
                  value,
                  label,
                }))}
              />
            </>
          ) : (
            <>
              <Input
                aria-label="流水批次号"
                value={filters.batchCode || ''}
                placeholder="按批次号查询"
                allowClear
                onChange={(e) =>
                  filter({ batchCode: e.target.value.trim() || undefined })
                }
              />
              <Select
                aria-label="流水类型"
                placeholder="全部交易类型"
                allowClear
                value={filters.txnType}
                onChange={(txnType) => filter({ txnType })}
                options={Object.entries(txnNames).map(([value, label]) => ({
                  value,
                  label,
                }))}
              />
              <DatePicker.RangePicker
                aria-label="流水时间范围"
                onChange={(dates) =>
                  filter({
                    from: dates?.[0]?.format('YYYY-MM-DD'),
                    to: dates?.[1]?.format('YYYY-MM-DD'),
                  })
                }
              />
            </>
          )}
        </div>
        {error && (
          <Alert
            type="error"
            message="库存查询失败"
            description={error}
            action={
              <Button onClick={() => setRevision((r) => r + 1)}>重试</Button>
            }
          />
        )}
        {view === 'stocks' ? (
          <Table<Stock>
            rowKey="itemId"
            loading={loading}
            dataSource={data.records as Stock[]}
            scroll={{ x: 1280 }}
            size="middle"
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="没有符合条件的库存材料"
                />
              ),
            }}
            columns={[
              {
                title: '材料档案',
                width: 240,
                render: (_, r) => (
                  <button
                    className="material-name-link"
                    onClick={() => setSelected(r.itemId)}
                  >
                    <span className="material-cell-stack">
                      <strong>{r.name}</strong>
                      <small>{r.materialCode}</small>
                    </span>
                  </button>
                ),
              },
              {
                title: '品牌 / 型号',
                render: (_, r) => (
                  <div className="material-cell-stack">
                    <span>{r.brand || '—'}</span>
                    <small>{r.model || '—'}</small>
                  </div>
                ),
              },
              { title: '分类', dataIndex: 'categoryPath' },
              {
                title: '当前库存',
                render: (_, r) => (
                  <strong>
                    {quantity(r.quantity)} {r.unit}
                  </strong>
                ),
              },
              {
                title: '安全库存',
                render: (_, r) => quantity(r.safeStock) + ' ' + r.unit,
              },
              {
                title: '库存状态',
                render: (_, r) => (
                  <Tag
                    color={
                      r.stockStatus === 'NORMAL'
                        ? 'success'
                        : r.stockStatus === 'LOW'
                          ? 'warning'
                          : 'default'
                    }
                  >
                    {stockNames[r.stockStatus]}
                  </Tag>
                ),
              },
              { title: '批次', dataIndex: 'batchCount' },
              {
                title: '最近入库',
                render: (_, r) =>
                  r.lastReceivedAt ? dateTime(r.lastReceivedAt) : '未入库',
              },
            ]}
            pagination={{
              current: filters.page,
              pageSize: filters.size,
              total: data.total,
              showSizeChanger: true,
              showTotal: (t) => '共 ' + t + ' 种',
              onChange: (page, size) =>
                setFilters((f) => ({
                  ...f,
                  page: size === f.size ? page : 1,
                  size,
                })),
            }}
          />
        ) : (
          <Table<Transaction>
            rowKey="id"
            loading={loading}
            dataSource={data.records as Transaction[]}
            scroll={{ x: 1600 }}
            size="middle"
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无匹配的库存流水"
                />
              ),
            }}
            columns={[
              { title: '交易编号', dataIndex: 'transactionNo', width: 180 },
              {
                title: '材料',
                render: (_, t) => (
                  <div>
                    {t.itemName}
                    <small className="stock-block">{t.itemCode}</small>
                  </div>
                ),
              },
              {
                title: '批次',
                render: (_, t) => (
                  <Link to={'/stock/batches/' + t.batchCode}>
                    {t.batchCode}
                  </Link>
                ),
              },
              {
                title: '类型',
                render: (_, t) => <Tag color="blue">{txnNames[t.txnType]}</Tag>,
              },
              {
                title: '变化数量',
                render: (_, t) => quantity(t.qty) + ' ' + t.unit,
              },
              {
                title: '批次余额（前 → 后）',
                render: (_, t) =>
                  quantity(t.beforeQuantity) +
                  ' → ' +
                  quantity(t.afterQuantity),
              },
              { title: '操作人', dataIndex: 'operatorName' },
              {
                title: '关联单据',
                render: (_, t) => (
                  <Link
                    to={
                      t.referenceType === 'RETURN' && user?.modules.includes('returns') ? '/returns?record=' + t.orderId : t.referenceType === 'SCRAP' && user?.roles.some(r => ['ADMIN','LAB_TECH'].includes(r)) ? '/returns?tab=scrap&record=' + t.orderId : t.referenceType === 'ISSUE' && user?.modules.includes('requisitions') ? '/requisitions?order=' + t.orderId : t.referenceType === 'INBOUND' && user?.modules.includes('inbound') ? '/inbound?order=' + t.orderId : '/stock/batches/' + t.batchCode
                    }
                  >
                    {t.referenceNo}
                  </Link>
                ),
              },
              { title: '操作时间', render: (_, t) => dateTime(t.createdAt) },
            ]}
            pagination={{
              current: filters.page,
              pageSize: filters.size,
              total: data.total,
              showSizeChanger: true,
              showTotal: (t) => '共 ' + t + ' 条',
              onChange: (page, size) =>
                setFilters((f) => ({
                  ...f,
                  page: size === f.size ? page : 1,
                  size,
                })),
            }}
          />
        )}
        <p className="stock-hint">
          库存由批次余额实时汇总。有效期仅作信息展示；包含真实入库与领用流水。
        </p>
      </section>
      <DrawerPresence>{selected !== undefined && (
        <StockDrawer itemId={selected} onClose={() => setSelected(undefined)} />
      )}</DrawerPresence>
    </div>
  )
}
