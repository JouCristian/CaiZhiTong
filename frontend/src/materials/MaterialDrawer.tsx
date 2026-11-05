import { MotionDrawer as Drawer } from '../motion/MotionDrawer'
import { QuotaEditor } from '../issues/QuotaEditor'
import { useAuth } from '../auth'
import { StockPeek } from '../stock/BatchViews'
import { useEffect, useState } from 'react'
import { Alert, App, Button, Descriptions, Form, Input, InputNumber, Result, Select, Skeleton, Space, Tag, TreeSelect } from 'antd'
import { DatabaseOutlined, EditOutlined } from '@ant-design/icons'
import { request } from '../api'
import type { Category, Material, MaterialInput } from './types'
import { categoryOptions, dateTime, money, quantity, statusNames } from './types'
export type MaterialEditor = { mode: 'create' | 'edit' | 'detail'; id?: number; categoryId?: number }
interface Props {
  editor: MaterialEditor; categories: Category[]; canWrite: boolean
  onClose: () => void; onSaved: (material: Material, created: boolean) => void
}
export function MaterialDrawer({ editor, categories, canWrite, onClose, onSaved }: Props) {
  const { user } = useAuth()
  const [quotaOpen, setQuotaOpen] = useState(false)
  const [mode, setMode] = useState(editor.mode)
  const [material, setMaterial] = useState<Material | null>(null)
  const [loading, setLoading] = useState(editor.mode !== 'create')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [retry, setRetry] = useState(0)
  const { message } = App.useApp()
  const [form] = Form.useForm<MaterialInput>()
  useEffect(() => {
    if (!editor.id) return
    let active = true
    setLoading(true); setError('')
    request<Material>('/api/materials/' + editor.id).then(row => { if (active) { setMaterial(row); setLoading(false) } })
      .catch(e => { if (active) { setError((e as Error).message); setLoading(false) } })
    return () => { active = false }
  }, [editor.id, retry])
  async function save(values: MaterialInput) {
    setSaving(true); setError('')
    try {
      const row = await request<Material>('/api/materials' + (editor.id ? '/' + editor.id : ''), {
        method: editor.id ? 'PUT' : 'POST', body: JSON.stringify(values),
      })
      void message.success(editor.id ? '材料档案已更新' : '材料已创建，编码已自动生成')
      onSaved(row, !editor.id)
    } catch (e) { setError((e as Error).message) }
    finally { setSaving(false) }
  }
  const readonly = mode === 'detail'
  const initial = material ? { ...material, status: material.status as 'ACTIVE' | 'DISABLED' } : {
    categoryId: editor.categoryId, unit: '件', unitPrice: '0.0000', currency: 'CNY', safeStock: '0', status: 'ACTIVE' as const,
  }
  return <Drawer title={mode === 'create' ? '新增材料' : readonly ? '材料详情' : '编辑材料'} open onClose={onClose}
    width="min(640px, 100vw)" maskClosable={!saving} keyboard={!saving}
    footer={loading ? null : readonly ? <div className="drawer-actions"><Button onClick={onClose}>关闭详情</Button>{canWrite && material?.status !== 'ARCHIVED' && <Button type="primary" aria-label="编辑档案" icon={<EditOutlined aria-hidden />} onClick={() => setMode('edit')}>编辑档案</Button>}</div> :
      <div className="drawer-actions"><Button onClick={onClose} disabled={saving}>取消</Button><Button type="primary" loading={saving} onClick={() => form.submit()}>保存材料</Button></div>}>
    {loading ? <Skeleton active paragraph={{ rows: 10 }} /> : error && !material && mode !== 'create' ? <Result status="warning" title="档案加载失败" subTitle={error} extra={<Button onClick={() => setRetry(r => r + 1)}>重新加载</Button>} /> :
      readonly && material ? <>
        <div className="material-detail-heading"><span className="detail-material-icon"><DatabaseOutlined /></span><div><h2>{material.name}</h2><p>{material.materialCode}</p></div><Tag color={material.status === 'ACTIVE' ? 'success' : 'default'}>{statusNames[material.status]}</Tag></div>
        <StockPeek itemId={material.id} /><div className="stock-peek"><span>每人每月限额：{material.monthlyIssueLimit ? quantity(material.monthlyIssueLimit) + ' ' + material.unit : '不限制'}</span>{user?.roles.includes('ADMIN') && material.status !== 'ARCHIVED' && <Button type="link" onClick={() => setQuotaOpen(true)}>设置领用限额</Button>}</div>
        <Descriptions column={1} className="material-descriptions" items={[
          { key: 'id', label: '材料 ID', children: material.id },
          { key: 'code', label: '材料编码', children: material.materialCode },
          { key: 'category', label: '所属类目', children: material.categoryPath },
          { key: 'brand', label: '品牌', children: material.brand || '未填写' },
          { key: 'model', label: '型号', children: material.model || '未填写' },
          { key: 'spec', label: '规格参数', children: <span className="preserve-lines">{material.spec || '未填写'}</span> },
          { key: 'unit', label: '单位', children: material.unit },
          { key: 'price', label: '单价', children: money(material.unitPrice, material.currency) },
          { key: 'currency', label: '币种', children: material.currency },
          { key: 'safe', label: '安全库存', children: quantity(material.safeStock) + ' ' + material.unit },
          { key: 'description', label: '备注说明', children: <span className="preserve-lines">{material.description || '未填写'}</span> },
          { key: 'created', label: '创建时间', children: dateTime(material.createdAt) },
          { key: 'updated', label: '更新时间', children: dateTime(material.updatedAt) },
        ]} />
        {material.status === 'ARCHIVED' && <Alert type="info" showIcon message="该档案已归档，历史记录保留，仅供查看。" />}
      </> : <>
        {error && <Alert type="error" showIcon message={error} className="form-alert" />}
        {!categories.length && <Alert type="info" showIcon message="尚无类目，请由管理员先建立分类。" className="form-alert" />}
        <div className="material-form-note">{material ? <>材料编码 <strong>{material.materialCode}</strong> 保持不变。</> : '材料编码由系统在保存时自动生成。带 * 的字段为必填项。'}</div>
        <Form form={form} layout="vertical" initialValues={initial} onFinish={save} disabled={saving} scrollToFirstError>
          <Form.Item label="材料名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入材料名称' }, { max: 100, message: '最多 100 个字符' }]}><Input placeholder="例如：金属膜电阻 10kΩ" maxLength={100} /></Form.Item>
          <Form.Item label="所属类目" name="categoryId" rules={[{ required: true, message: '请选择所属类目' }]}><TreeSelect treeData={categoryOptions(categories)} treeDefaultExpandAll placeholder="选择大类或子类" showSearch treeNodeFilterProp="title" /></Form.Item>
          <div className="material-form-grid"><Form.Item label="品牌" name="brand" rules={[{ max: 100 }]}><Input placeholder="例如：国巨" maxLength={100} /></Form.Item><Form.Item label="型号" name="model" rules={[{ max: 100 }]}><Input placeholder="例如：MFR-25" maxLength={100} /></Form.Item></div>
          <Form.Item label="规格参数" name="spec" rules={[{ max: 2000 }]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="阻值、精度、尺寸、接口等参数" /></Form.Item>
          <div className="material-form-grid"><Form.Item label="单位" name="unit" rules={[{ required: true, whitespace: true, message: '请输入单位' }, { max: 20 }]}><Input placeholder="件 / 个 / 米" maxLength={20} /></Form.Item>
            <Form.Item label="安全库存" name="safeStock" rules={[{ required: true, message: '请输入安全库存' }]} extra="阈值，不是当前库存"><InputNumber stringMode min="0" max="999999999.999" precision={3} style={{ width: '100%' }} /></Form.Item></div>
          <div className="material-form-grid"><Form.Item label="单价" name="unitPrice" rules={[{ required: true, message: '请输入单价' }]} extra="支持最多四位小数"><InputNumber stringMode min="0" max="9999999999.9999" precision={4} style={{ width: '100%' }} /></Form.Item>
            <Form.Item label="币种" name="currency" rules={[{ required: true }]}><Select options={['CNY', 'USD', 'EUR', 'JPY', 'HKD'].map(value => ({ value, label: value }))} /></Form.Item></div>
          <Form.Item label="状态" name="status" rules={[{ required: true }]}><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
          <Form.Item label="备注说明" name="description" rules={[{ max: 2000 }]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="用途、存放建议或其他说明" /></Form.Item>
        </Form>
        <Space className="material-form-note">归档由管理员在列表中操作，归档后历史档案仍保留。</Space>
      </>}
    {quotaOpen && material && <QuotaEditor material={material} onClose={() => setQuotaOpen(false)} onSaved={setMaterial} />}
  </Drawer>
}



