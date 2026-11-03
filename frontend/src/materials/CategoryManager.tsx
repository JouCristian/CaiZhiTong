import { useState } from 'react'
import type { Key } from 'react'
import { Alert, App, Button, Descriptions, Drawer, Empty, Form, Input, InputNumber, Skeleton, Tag, Tree, TreeSelect } from 'antd'
import { DeleteOutlined, EditOutlined, FolderOutlined, PlusOutlined } from '@ant-design/icons'
import type { DataNode } from 'antd/es/tree'
import { request } from '../api'
import type { Category } from './types'
import { dateTime, flattenCategories } from './types'
interface InputValues { parentId?: number; name: string; description?: string; sortOrder: number }
interface Props { categories: Category[]; loading: boolean; admin: boolean; onChanged: () => void }
export function CategoryManager({ categories, loading, admin, onChanged }: Props) {
  const flat = flattenCategories(categories)
  const [selectedId, setSelectedId] = useState<number | undefined>()
  const [expanded, setExpanded] = useState<Key[]>([])
  const [editing, setEditing] = useState<{ id?: number; values: InputValues } | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [form] = Form.useForm<InputValues>()
  const { message, modal } = App.useApp()
  const selected = flat.find(c => c.id === selectedId) || categories[0]
  function openForm(row?: Category, parentId?: number) {
    const values = row ? { parentId: row.parentId, name: row.name, description: row.description, sortOrder: row.sortOrder } : { parentId, name: '', description: '', sortOrder: 0 }
    setEditing({ id: row?.id, values }); setError(''); form.setFieldsValue(values)
  }
  async function save(values: InputValues) {
    setSaving(true); setError('')
    try {
      const row = await request<Category>('/api/material-categories' + (editing?.id ? '/' + editing.id : ''), {
        method: editing?.id ? 'PUT' : 'POST', body: JSON.stringify({ ...values, parentId: values.parentId ?? null }),
      })
      setSelectedId(row.id); setEditing(null); onChanged()
      if (row.parentId) setExpanded(keys => [...new Set([...keys, row.parentId!])])
      void message.success(editing?.id ? '类目信息已更新' : '类目已创建')
    } catch (e) { setError((e as Error).message) }
    finally { setSaving(false) }
  }
  function remove(row: Category) {
    modal.confirm({
      title: '删除空类目「' + row.name + '」？', content: '仅可删除没有子类、没有关联材料的类目。', okText: '删除类目', cancelText: '取消', okButtonProps: { danger: true },
      onOk: async () => {
        try { await request('/api/material-categories/' + row.id, { method: 'DELETE' }); setSelectedId(undefined); onChanged(); void message.success('空类目已删除') }
        catch (e) { void message.error((e as Error).message); throw e }
      },
    })
  }
  const nodes = (rows: Category[]): DataNode[] =>
    rows.map(c => ({ key: c.id, title: <span className="category-tree-label" title={c.name}><span className="category-tree-name">{c.name}</span><span>{c.materialCount}</span></span>, icon: <FolderOutlined />, children: nodes(c.children) }))
  return <>
    <div className="catalog-intro"><div><h2>材料类目</h2><p>维护大类与子类。品牌、型号随材料属性组织，避免重复维护。</p></div>{admin && <Button type="primary" aria-label="新增类目" icon={<PlusOutlined aria-hidden />} onClick={() => openForm()}>新增类目</Button>}</div>
    <div className="category-manager">
      <section className="surface category-tree-panel"><div className="catalog-panel-heading"><h3>基础分类</h3><div><Button type="text" size="small" onClick={() => setExpanded(flat.map(c => c.id))}>展开</Button><Button type="text" size="small" onClick={() => setExpanded([])}>折叠</Button></div></div>
        {loading ? <Skeleton active className="catalog-skeleton" /> : !categories.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未建立类目" /> : <Tree showIcon blockNode treeData={nodes(categories)} expandedKeys={expanded} onExpand={setExpanded} selectedKeys={selected ? [selected.id] : []} onSelect={keys => { if (keys.length) setSelectedId(Number(keys[0])) }} />}
        <div className="category-tree-foot">数字表示直接关联的材料档案，包含归档记录。</div>
      </section>
      <section className="surface category-detail-panel">{loading ? <Skeleton active /> : selected ? <>
        <div className="category-detail-heading"><span className="detail-material-icon"><FolderOutlined /></span><div><Tag>{selected.parentId ? '子类' : '大类'}</Tag><h2>{selected.name}</h2></div></div>
        <Descriptions column={1} items={[
          { key: 'parent', label: '父级类目', children: selected.parentId ? flat.find(c => c.id === selected.parentId)?.name : '无，作为大类' },
          { key: 'count', label: '关联材料', children: selected.materialCount + ' 份档案' },
          { key: 'child', label: '下级类目', children: selected.children.length + ' 个子类' },
          { key: 'sort', label: '排序值', children: selected.sortOrder },
          { key: 'description', label: '描述', children: <span className="preserve-lines">{selected.description || '未填写描述'}</span> },
          { key: 'created', label: '创建时间', children: dateTime(selected.createdAt) },
          { key: 'updated', label: '更新时间', children: dateTime(selected.updatedAt) },
        ]} />
        {admin && <div className="category-detail-actions"><Button aria-label="编辑类目" icon={<EditOutlined aria-hidden />} onClick={() => openForm(selected)}>编辑类目</Button>{!selected.parentId && <Button aria-label="新增子类" icon={<PlusOutlined aria-hidden />} onClick={() => openForm(undefined, selected.id)}>新增子类</Button>}<Button danger aria-label="删除类目" icon={<DeleteOutlined aria-hidden />} disabled={selected.children.length > 0 || selected.materialCount > 0} onClick={() => remove(selected)}>删除类目</Button></div>}
        {selected.children.length > 0 || selected.materialCount > 0 ? <div className="detail-notice">该类目仍有子类或关联材料，受到删除保护。归档材料也保留分类关联。</div> : <div className="detail-notice">空类目可由管理员删除。调整父级时，系统会校验层级与循环引用。</div>}
      </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="创建首个大类，让材料有序归类">{admin && <Button onClick={() => openForm()}>创建首个类目</Button>}</Empty>}</section>
    </div>
    {editing && <Drawer open title={editing.id ? '编辑类目' : '新增类目'} width="min(500px, 100vw)" onClose={() => !saving && setEditing(null)} maskClosable={!saving}
      footer={<div className="drawer-actions"><Button onClick={() => setEditing(null)} disabled={saving}>取消</Button><Button type="primary" loading={saving} onClick={() => form.submit()}>保存类目</Button></div>}>
      {error && <Alert type="error" showIcon message={error} className="form-alert" />}
      <div className="material-form-note">基础分类最多两层。可将大类设为父级，品牌与型号在材料表单中维护。</div>
      <Form form={form} layout="vertical" onFinish={save} initialValues={editing.values} disabled={saving}>
        <Form.Item name="name" label="分类名称" rules={[{ required: true, whitespace: true, message: '请输入分类名称' }, { max: 100 }]}><Input maxLength={100} placeholder="例如：电子元器件" /></Form.Item>
        <Form.Item name="parentId" label="父级类目"><TreeSelect allowClear placeholder="无，作为大类" treeData={categories.filter(c => c.id !== editing.id).map(c => ({ value: c.id, title: c.name }))} /></Form.Item>
        <Form.Item name="sortOrder" label="排序值" rules={[{ required: true }]} extra="数值越小，排列越靠前"><InputNumber min={0} max={9999} precision={0} /></Form.Item>
        <Form.Item name="description" label="描述" rules={[{ max: 2000 }]}><Input.TextArea rows={5} maxLength={2000} showCount placeholder="说明该类目适用的材料范围" /></Form.Item>
      </Form>
    </Drawer>}
  </>
}

