import { useEffect, useState } from 'react'
import { Alert, Button, Tabs } from 'antd'
import { DatabaseOutlined, FolderOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import { request } from '../api'
import { useAuth } from '../auth'
import { CategoryManager } from './CategoryManager'
import { MaterialList } from './MaterialList'
import type { Category, Facet } from './types'
import './materials.css'
export function MaterialsPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const [categories, setCategories] = useState<Category[]>([])
  const [facets, setFacets] = useState<Facet[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const admin = !!user?.roles.includes('ADMIN')
  const canWrite = admin || !!user?.roles.includes('LAB_TECH')
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    Promise.all([request<Category[]>('/api/material-categories/tree'), request<Facet[]>('/api/materials/facets')]).then(([tree, groups]) => {
      if (active) { setCategories(tree); setFacets(groups); setLoading(false) }
    }).catch(e => { if (active) { setError((e as Error).message); setLoading(false) } })
    return () => { active = false }
  }, [revision])
  const changed = () => setRevision(value => value + 1)
  return <div className="page-enter materials-page">
    <div className="page-heading"><div><h1>材料管理</h1><p>从清晰的分类，到每一份完整的材料档案。</p></div><span className="catalog-permission"><SafetyCertificateOutlined />{admin ? '管理员 · 完整维护' : canWrite ? '实验员 · 档案维护' : '只读访问'}</span></div>
    {error && <Alert className="form-alert" type="error" showIcon message="分类与品牌信息加载失败" description={error} action={<Button onClick={changed}>重新加载</Button>} />}
    <Tabs activeKey={params.get('view') === 'categories' ? 'categories' : 'items'} onChange={key => setParams(key === 'categories' ? { view: 'categories' } : {})} items={[
      { key: 'items', label: <span><DatabaseOutlined /> 材料档案</span>, children: <MaterialList categories={categories} facets={facets} metaLoading={loading} canWrite={canWrite} admin={admin} revision={revision} onChanged={changed} /> },
      { key: 'categories', label: <span><FolderOutlined /> 类目管理</span>, children: <CategoryManager categories={categories} loading={loading} admin={admin} onChanged={changed} /> },
    ]} />
  </div>
}

