import type { Stock, Page } from '../stock/types'
import { useEffect, useMemo, useState } from 'react'
import type { Key } from 'react'
import { Alert, App, Button, Empty, Input, Select, Skeleton, Table, Tag, Tooltip, Tree } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { DataNode } from 'antd/es/tree'
import { DatabaseOutlined, EditOutlined, EyeOutlined, FolderOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { request } from '../api'
import { MaterialDrawer } from './MaterialDrawer'
import type { MaterialEditor } from './MaterialDrawer'
import type { Category, Facet, Material, MaterialPage, MaterialStatus } from './types'
import { dateTime, money, quantity, statusNames } from './types'
type Filters = { page: number; size: number; keyword?: string; categoryId?: number; brand?: string; model?: string; status?: string; order?: string }
interface Props { categories: Category[]; facets: Facet[]; metaLoading: boolean; canWrite: boolean; admin: boolean; revision: number; onChanged: () => void }
export function MaterialList({ categories, facets, metaLoading, canWrite, admin, revision, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [filters, setFilters] = useState<Filters>({ page: 1, size: 20 })
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState<MaterialPage>({ records: [], total: 0, page: 1, size: 20 })
  const [stocks, setStocks] = useState<Record<number, string>>({})
  const [stockError, setStockError] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [expanded, setExpanded] = useState<Key[]>([])
  const [selectedKeys, setSelectedKeys] = useState<Key[]>(['all'])
  const [editor, setEditor] = useState<MaterialEditor | null>(null)
  const [pending, setPending] = useState<number | null>(null)
  const filter = (values: Partial<Filters>) => setFilters(previous => ({ ...previous, ...values, page: 1 }))
  useEffect(() => {
    const timer = window.setTimeout(() => setFilters(current => current.keyword === (keyword.trim() || undefined) ? current : { ...current, page: 1, keyword: keyword.trim() || undefined }), 350)
    return () => window.clearTimeout(timer)
  }, [keyword])
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)) })
    request<MaterialPage>('/api/materials?' + params.toString()).then(data => {
      if (!active) return
      if (data.total > 0 && data.records.length === 0 && filters.page > 1) { setFilters(f => ({ ...f, page: Math.ceil(data.total / f.size) })); return }
      setPage(data); setLoading(false); setStocks({}); setStockError('')
      if (data.records.length) request<Page<Stock>>('/api/stocks?size=100&materialIds=' + data.records.map(m => m.id).join(',')).then(d => { if (active) setStocks(Object.fromEntries(d.records.map(s => [s.itemId, s.quantity]))) }).catch(e => { if (active) setStockError((e as Error).message) })
    }).catch(e => { if (active) { setError((e as Error).message); setPage(p => ({ ...p, records: [], total: 0 })); setLoading(false) } })
    return () => { active = false }
  }, [filters, revision, retry])
  const taxonomy = useMemo(() => {
    const selections = new Map<string, Partial<Filters>>()
    const keys: Key[] = []
    const nodes = (rows: Category[]): DataNode[] => rows.map(c => {
      const key = 'category:' + c.id
      selections.set(key, { categoryId: c.id, brand: undefined, model: undefined }); keys.push(key)
      const byBrand = new Map<string, Facet[]>()
      facets.filter(f => f.categoryId === c.id && f.brand).forEach(f => byBrand.set(f.brand!, [...(byBrand.get(f.brand!) || []), f]))
      const brandNodes: DataNode[] = [...byBrand.entries()].map(([brand, groups]) => {
        const brandKey = JSON.stringify(['brand', c.id, brand])
        selections.set(brandKey, { categoryId: c.id, brand, model: undefined }); keys.push(brandKey)
        return { key: brandKey, title: brand, children: groups.filter(g => g.model).map(g => {
          const modelKey = JSON.stringify(['model', c.id, brand, g.model])
          selections.set(modelKey, { categoryId: c.id, brand, model: g.model }); keys.push(modelKey)
          return { key: modelKey, title: g.model }
        }) }
      })
      return { key, title: c.name, icon: <FolderOutlined />, children: [...nodes(c.children), ...brandNodes] }
    })
    return { nodes: nodes(categories), selections, keys }
  }, [categories, facets])
  const brands = [...new Set(facets.flatMap(f => f.brand ? [f.brand] : []))].sort((a, b) => a.localeCompare(b, 'zh-CN'))
  function selectTree(keys: Key[]) {
    const key = String(keys[0] || 'all')
    setSelectedKeys([key])
    filter(key === 'all' ? { categoryId: undefined, brand: undefined, model: undefined } : taxonomy.selections.get(key) || {})
  }
  async function changeStatus(row: Material, status: MaterialStatus) {
    setPending(row.id)
    try { await request<Material>('/api/materials/' + row.id + '/status', { method: 'PATCH', body: JSON.stringify({ status }) }); onChanged(); void message.success(status === 'ARCHIVED' ? '材料已归档，历史记录已保留' : '材料状态已更新') }
    catch (e) { void message.error((e as Error).message); throw e }
    finally { setPending(null) }
  }
  function archive(row: Material) {
    modal.confirm({ title: '归档「' + row.name + '」？', content: '归档后仅可查看，材料 ID、编码和历史关联都会保留。', okText: '确认归档', cancelText: '取消', okButtonProps: { danger: true }, onOk: () => changeStatus(row, 'ARCHIVED') })
  }
  const columns: ColumnsType<Material> = [
    { title: '材料档案', key: 'name', width: 240, render: (_, row) => <button className="material-name-link" onClick={() => setEditor({ mode: 'detail', id: row.id })}><span className="material-table-icon"><DatabaseOutlined /></span><span><strong>{row.name}</strong><small>{row.materialCode}</small></span></button> },
    { title: '所属类目', dataIndex: 'categoryPath', width: 180, ellipsis: true },
    { title: '品牌 / 型号', key: 'brand', width: 150, render: (_, row) => <div className="material-cell-stack"><span>{row.brand || '未填写'}</span><small>{row.model || '—'}</small></div> },
    { title: '单价', key: 'price', width: 160, render: (_, row) => <span className="money-value">{money(row.unitPrice, row.currency)}<small> / {row.unit}</small></span> },
    { title: '安全库存', key: 'safe', width: 105, render: (_, row) => quantity(row.safeStock) + ' ' + row.unit },
    { title: '当前库存', key: 'stock', width: 100, render: (_, row) => <span className="not-stocked">{stocks[row.id] !== undefined ? quantity(stocks[row.id]) + ' ' + row.unit : stockError ? '读取失败' : '读取中…'}</span> },
    { title: '状态', key: 'status', width: 90, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'success' : 'default'}>{statusNames[row.status]}</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', width: 150, render: dateTime },
    { title: '操作', key: 'actions', width: admin ? 218 : canWrite ? 175 : 60, fixed: 'right', render: (_, row) => <div className="material-row-actions">
      <Tooltip title="查看详情"><Button type="text" size="small" icon={<EyeOutlined />} aria-label={'查看 ' + row.name} onClick={() => setEditor({ mode: 'detail', id: row.id })} /></Tooltip>
      {canWrite && row.status !== 'ARCHIVED' && <><Tooltip title="编辑档案"><Button type="text" size="small" icon={<EditOutlined />} aria-label={'编辑 ' + row.name} onClick={() => setEditor({ mode: 'edit', id: row.id })} /></Tooltip><Button type="text" size="small" loading={pending === row.id} onClick={() => void changeStatus(row, row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE').catch(() => {})}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button></>}
      {admin && row.status !== 'ARCHIVED' && <Button type="text" size="small" danger disabled={pending === row.id} onClick={() => archive(row)}>归档</Button>}
    </div> },
  ]
  function reset() { setKeyword(''); setFilters({ page: 1, size: filters.size }); setSelectedKeys(['all']) }
  return <div className="material-workspace">
    <aside className="surface material-taxonomy"><div className="catalog-panel-heading"><h3>分类浏览</h3><Button type="text" size="small" onClick={() => setExpanded(expanded.length ? [] : taxonomy.keys)}>{expanded.length ? '折叠' : '展开'}</Button></div>
      {metaLoading ? <Skeleton active className="catalog-skeleton" /> : <Tree showIcon blockNode expandedKeys={expanded} onExpand={setExpanded} selectedKeys={selectedKeys} onSelect={selectTree} treeData={[{ key: 'all', title: '全部材料', icon: <DatabaseOutlined /> }, ...taxonomy.nodes]} />}
      <div className="category-tree-foot">大类 → 子类 → 品牌 → 型号<br />品牌、型号来自真实材料属性。</div>
    </aside>
    <section className="surface material-list-panel"><div className="material-list-top"><div><h2>材料档案</h2><span>共 {page.total} 份档案</span></div><div className="material-list-buttons"><Tooltip title="重新加载"><Button icon={<ReloadOutlined />} aria-label="刷新材料列表" onClick={() => { onChanged(); setRetry(r => r + 1) }} /></Tooltip>{canWrite && <Button type="primary" aria-label="新增材料" icon={<PlusOutlined aria-hidden />} onClick={() => setEditor({ mode: 'create', categoryId: filters.categoryId })}>新增材料</Button>}</div></div>
      <div className="material-filters"><Input.Search aria-label="搜索名称或编码" placeholder="搜索材料名称或编码" maxLength={100} allowClear value={keyword} onChange={e => setKeyword(e.target.value)} onSearch={() => filter({ keyword: keyword.trim() || undefined })} />
        <Select aria-label="筛选品牌" placeholder="全部品牌" allowClear value={filters.brand} onChange={brand => { filter({ brand, model: undefined }); setSelectedKeys(filters.categoryId ? ['category:' + filters.categoryId] : ['all']) }} options={brands.map(value => ({ value, label: value }))} />
        <Select aria-label="筛选状态" placeholder="全部状态" allowClear value={filters.status} onChange={status => filter({ status })} options={Object.entries(statusNames).map(([value, label]) => ({ value, label }))} />
        <Select aria-label="创建时间排序" value={filters.order || 'desc'} onChange={order => filter({ order })} options={[{ value: 'desc', label: '最新创建' }, { value: 'asc', label: '最早创建' }]} /></div>
      {(filters.categoryId || filters.brand || filters.model || filters.status || filters.keyword) && <div className="active-material-filters"><span>正在按条件查询{filters.model ? ' · 型号 ' + filters.model : ''}</span><Button type="link" size="small" onClick={reset}>清除筛选</Button></div>}
      {stockError && <Alert type="warning" showIcon message="库存读取失败" description={stockError} action={<Button onClick={() => setRetry(r => r + 1)}>重试</Button>} />}
      {error && <Alert type="error" showIcon message="材料列表加载失败" description={error} action={<Button onClick={() => setRetry(r => r + 1)}>重试</Button>} className="material-list-error" />}
      <Table<Material> rowKey="id" columns={columns} dataSource={page.records} loading={loading} size="middle"
        scroll={{ x: admin ? 1493 : 1400, y: 420 }} onRow={row => ({ 'data-testid': 'material-row-' + row.id } as React.HTMLAttributes<HTMLTableRowElement>)}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={error ? '暂时无法读取材料' : filters.keyword || filters.categoryId || filters.brand || filters.status ? '没有符合条件的材料' : '材料库还是空的，先建立类目，再创建第一份材料档案'}>{!error && !filters.keyword && canWrite && <Button type="link" onClick={() => setEditor({ mode: 'create', categoryId: filters.categoryId })}>新增材料档案</Button>}</Empty> }}
        pagination={{ current: filters.page, pageSize: filters.size, total: page.total, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: total => '共 ' + total + ' 条', onChange: (current, size) => setFilters(f => ({ ...f, page: size === f.size ? current : 1, size })), size: 'small' }} />
      <div className="material-list-foot">档案不记录可手动修改的库存数量。库存来自批次余额汇总，可在档案详情中查看。</div>
    </section>
    {editor && <MaterialDrawer key={editor.mode + ':' + (editor.id || 'new')} editor={editor} categories={categories} canWrite={canWrite} onClose={() => setEditor(null)} onSaved={(_, created) => { setEditor(null); if (created) reset(); onChanged() }} />}
  </div>
}


