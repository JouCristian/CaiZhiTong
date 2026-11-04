import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Input, Select, Skeleton, Table, Tag, Timeline } from 'antd'
import { ArrowRightOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { request } from '../api'
import { useAuth } from '../auth'
import { money } from '../materials/types'
import Decimal from 'decimal.js'
import './demo.css'

type Material = { id: number; materialCode: string; name: string; categoryName: string; brand: string; model: string; spec: string; unit: string; unitPrice: string; currency: string; safeStock: string; quantity: string; stockValue: string; stockStatus: string; description: string; batches?: Batch[] }
type Batch = { id: number; batchCode: string; quantity: string; initialQuantity: string; unitPrice: string; location: string; receivedOn: string; events?: { id: number; eventType: string; quantity: string; occurredOn: string; description: string }[] }
type Dashboard = { metrics: { materials: number; stocked: number; low: number; zero: number }; values: { currency: string; value: string }[]; categories: { name: string; count: number }[]; batchCount: number }
type Page = { records: Material[]; total: number; page: number; size: number }
const states: Record<string, string> = { NORMAL: '库存正常', LOW: '低库存', ZERO: '零库存' }
const amount = (value: string, currency: string) => money(new Decimal(value).toFixed(2), currency)

export function DemoPage({ view }: { view: 'dashboard' | 'materials' | 'stocks' }) {
  const { user } = useAuth()
  const [dashboard, setDashboard] = useState<Dashboard>()
  const [data, setData] = useState<Page>()
  const [categories, setCategories] = useState<{ id: number; name: string }[]>([])
  const [filters, setFilters] = useState({ page: 1, size: 10, keyword: '', categoryId: undefined as number | undefined, stockStatus: undefined as string | undefined })
  const [loading, setLoading] = useState(true), [error, setError] = useState(''), [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<number>(), [detail, setDetail] = useState<Material>(), [batch, setBatch] = useState<Batch>()
  const [detailError, setDetailError] = useState(''), [detailRevision, setDetailRevision] = useState(0)
  useEffect(() => {
    let active = true
    setError(''); setLoading(true)
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)) })
    const work = view === 'dashboard'
      ? request<Dashboard>('/api/demo/dashboard').then(value => { if (active) setDashboard(value) })
      : Promise.all([request<Page>('/api/demo/' + view + '?' + params), request<{ id: number; name: string }[]>('/api/demo/categories')]).then(([page, cats]) => { if (active) { setData(page); setCategories(cats) } })
    work.catch(e => { if (active) setError((e as Error).message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [filters, view, revision])
  useEffect(() => {
    if (selected === undefined) return
    let active = true
    setDetail(undefined); setBatch(undefined); setDetailError('')
    request<Material>('/api/demo/materials/' + selected).then(value => { if (active) setDetail(value) }).catch(e => { if (active) setDetailError((e as Error).message) })
    return () => { active = false }
  }, [selected, detailRevision])
  async function openBatch(id: number) {
    setDetailError('')
    try { setBatch(await request<Batch>('/api/demo/batches/' + id)) } catch (e) { setDetailError((e as Error).message) }
  }
  const filter = (value: Partial<typeof filters>) => setFilters(current => ({ ...current, ...value, page: 1 }))
  return <div className="page-enter demo-page">
    <div className="page-heading"><div><h1>{view === 'dashboard' ? '演示工作台' : view === 'materials' ? '演示材料' : '演示库存'}</h1><p>探索材料管理的日常。这里的所有记录均为独立合成数据。</p></div><Tag color="blue" icon={<SafetyCertificateOutlined />}>公开演示 · 只读</Tag></div>
    <Alert type="info" showIcon message="独立演示空间" description="演示档案、库存和示例变化与正式业务完全分离，不包含真实人员、采购或领用信息。此空间不支持写入。" />
    {error ? <Alert type="error" showIcon message="演示数据加载失败" description={error} action={<Button onClick={() => setRevision(v => v + 1)}>重试加载</Button>} /> : null}
    {view === 'dashboard' ? <>
      <section className="surface demo-intro"><h2>你好，{user?.displayName}。</h2><p>从一颗电阻到一块开发板，了解档案、批次与库存如何连接。</p><Link to="/demo/materials"><Button type="primary">浏览演示材料 <ArrowRightOutlined /></Button></Link></section>
      {loading ? <Skeleton active /> : dashboard && <>
        <section className="metrics-panel" aria-label="演示指标">{[{ label: '合成材料', value: dashboard.metrics.materials }, { label: '演示批次', value: dashboard.batchCount }, { label: '低库存材料', value: dashboard.metrics.low }, { label: '零库存材料', value: dashboard.metrics.zero }].map(m => <div className="metric" key={m.label}><span className="metric-label">{m.label}</span><strong className="metric-value">{m.value}</strong></div>)}</section>
        <div className="demo-summary"><section className="surface"><div className="panel-heading"><h2>示例库存价值</h2><span>按批次单价计算 · 币种独立</span></div>{dashboard.values.map(v => <div className="demo-summary-row" key={v.currency}><span>{v.currency}</span><strong>{amount(v.value, v.currency)}</strong></div>)}</section><section className="surface"><div className="panel-heading"><h2>材料分类</h2><Link to="/demo/stocks">查看演示库存</Link></div>{dashboard.categories.map(c => <div className="demo-summary-row" key={c.name}><span>{c.name}</span><span>{c.count} 种</span></div>)}</section></div>
      </>}
    </> : <section className="surface demo-catalog">
      <div className="demo-filters"><Input.Search aria-label="搜索演示材料" placeholder="搜索名称、编码或型号" allowClear onSearch={keyword => filter({ keyword })} /><Select virtual={false} aria-label="演示分类" placeholder="全部分类" allowClear value={filters.categoryId} options={categories.map(c => ({ value: c.id, label: c.name }))} onChange={categoryId => filter({ categoryId })} /><Select virtual={false} aria-label="演示库存状态" placeholder="全部库存状态" allowClear value={filters.stockStatus} options={Object.entries(states).map(([value, label]) => ({ value, label }))} onChange={stockStatus => filter({ stockStatus })} /><Button icon={<ReloadOutlined />} aria-label="刷新演示数据" onClick={() => setRevision(v => v + 1)} /></div>
      <Table<Material> rowKey="id" loading={loading} dataSource={error ? [] : data?.records} scroll={{ x: 940 }} locale={{ emptyText: <Empty description="没有符合条件的演示材料，请调整筛选" /> }} pagination={{ current: filters.page, pageSize: filters.size, total: data?.total || 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: total => `共 ${total} 种合成材料`, onChange: (page, size) => setFilters(f => ({ ...f, page, size })) }} columns={[
        { title: '材料', key: 'name', width: 240, render: (_, m) => <><Button type="link" className="demo-material-link" onClick={() => setSelected(m.id)}>{m.name}</Button><div className="demo-code">{m.materialCode} · {m.model}</div></> },
        { title: '分类 / 品牌', key: 'category', render: (_, m) => <>{m.categoryName}<div className="demo-code">{m.brand}</div></> },
        { title: '参考单价', key: 'price', render: (_, m) => amount(m.unitPrice, m.currency) },
        { title: '示例库存', key: 'quantity', render: (_, m) => `${Number(m.quantity)} ${m.unit}` },
        { title: '库存状态', key: 'status', render: (_, m) => <Tag color={m.stockStatus === 'NORMAL' ? 'green' : m.stockStatus === 'LOW' ? 'orange' : 'default'}>{states[m.stockStatus]}</Tag> },
        { title: '操作', key: 'action', render: (_, m) => <Button type="link" onClick={() => setSelected(m.id)}>{view === 'stocks' ? '查看批次' : '查看详情'}</Button> },
      ]} />
    </section>}
    <Drawer title={detail?.name || '演示材料详情'} open={selected !== undefined} onClose={() => setSelected(undefined)} width={640} destroyOnClose>
      <Tag color="blue">公开演示 · 只读</Tag>
      {detailError && <Alert type="error" message={detailError} action={<Button onClick={() => { setDetailRevision(v => v + 1); setBatch(undefined) }}>重试详情</Button>} />}
      {!detail ? !detailError && <Skeleton active /> : <>
        <Descriptions column={1} className="demo-details" items={[
          { key: 'code', label: '演示编码', children: detail.materialCode }, { key: 'category', label: '分类', children: detail.categoryName },
          { key: 'model', label: '品牌 / 型号', children: detail.brand + ' / ' + detail.model }, { key: 'spec', label: '规格', children: detail.spec },
          { key: 'price', label: '参考单价', children: amount(detail.unitPrice, detail.currency) },
          { key: 'quantity', label: '示例库存 / 安全库存', children: `${Number(detail.quantity)} / ${Number(detail.safeStock)} ${detail.unit}` },
          { key: 'description', label: '说明', children: detail.description },
        ]} />
        <h3>演示批次</h3><Table<Batch> rowKey="id" size="small" pagination={false} scroll={{ x: 480 }} dataSource={detail.batches} columns={[
          { title: '批次编码', dataIndex: 'batchCode' }, { title: '数量', key: 'quantity', render: (_, b) => Number(b.quantity) + ' ' + detail.unit },
          { title: '库位', dataIndex: 'location' }, { title: '操作', key: 'action', render: (_, b) => <Button type="link" onClick={() => void openBatch(b.id)}>示例变化</Button> },
        ]} />
        {batch && <section className="demo-batch" aria-label="示例库存变化"><h3>{batch.batchCode} · 示例变化</h3><p>以下变化为合成示例，无关联人员或真实单据。</p><Timeline items={batch.events?.map(e => ({ children: <><strong>{e.eventType === 'IN' ? '示例入库' : '示例消耗'} {Number(e.quantity)} {detail.unit}</strong><p>{e.occurredOn} · {e.description}</p></> }))} /></section>}
      </>}
    </Drawer>
  </div>
}
