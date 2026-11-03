import { useEffect, useState } from 'react'
import {
  Alert,
  App,
  Button,
  DatePicker,
  Empty,
  Grid,
  Input,
  Pagination,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
} from 'antd'
import {
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import { request } from '../api'
import { useAuth } from '../auth'
import { dateTime, money } from '../materials/types'
import type { Page } from '../stock/types'
import { query } from '../stock/types'
import type { IssueOrder } from './types'
import { downloadIssue } from './types'
import { IssueCreator } from './IssueCreator'
import { IssueDetailDrawer } from './IssueDetailDrawer'
import './issues.css'
type Filters = {
  page: number
  size: number
  keyword?: string
  user?: string
  projectNo?: string
  from?: string
  to?: string
}
export function IssuesPage() {
  const { user } = useAuth(),
    { message } = App.useApp(),
    screens = Grid.useBreakpoint(),
    [params, setParams] = useSearchParams(),
    manage = !!user?.roles.some((r) => r === 'ADMIN' || r === 'LAB_TECH'),
    scope = manage && params.get('view') === 'all' ? 'all' : 'my'
  const code = params.get('batch') || undefined,
    orderParam = params.get('order'),
    selected =
      orderParam && /^\d+$/.test(orderParam) ? Number(orderParam) : undefined
  const [creating, setCreating] = useState(!!code),
    [filters, setFilters] = useState<Filters>({ page: 1, size: 20 }),
    [search, setSearch] = useState(''),
    [data, setData] = useState<Page<IssueOrder>>({
      records: [],
      total: 0,
      page: 1,
      size: 20,
    }),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [revision, setRevision] = useState(0),
    [downloading, setDownloading] = useState<number>()
  const filter = (v: Partial<Filters>) =>
    setFilters((f) => ({ ...f, ...v, page: 1 }))
  useEffect(() => {
    if (code) setCreating(true)
  }, [code])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    request<Page<IssueOrder>>(
      (scope === 'all' ? '/api/issues' : '/api/issues/my') + query(filters),
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
  }, [scope, filters, revision])
  function details(id: number) {
    setParams({
      ...(scope === 'all' ? { view: 'all' } : {}),
      order: String(id),
    })
  }
  async function download(row: IssueOrder) {
    setDownloading(row.id)
    try {
      await downloadIssue(row.id, row.issueNo)
    } catch (e) {
      void message.error((e as Error).message)
    } finally {
      setDownloading(undefined)
    }
  }
  const pagination = {
    current: filters.page,
    pageSize: filters.size,
    total: data.total,
    showSizeChanger: true,
    showTotal: (t: number) => '共 ' + t + ' 份领用单',
    onChange: (page: number, size: number) =>
      setFilters((f) => ({ ...f, page: size === f.size ? page : 1, size })),
  }
  return (
    <div className="page-enter issues-page">
      <div className="page-heading">
        <div>
          <h1>领用管理</h1>
          <p>从一个批次开始，让每次领用都有据可查。</p>
        </div>
        <Space>
          <Button
            aria-label="刷新领用记录"
            icon={<ReloadOutlined />}
            onClick={() => setRevision((r) => r + 1)}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreating(true)}
          >
            新建领用单
          </Button>
        </Space>
      </div>
      <section className="surface issue-history">
        <Tabs
          activeKey={scope}
          onChange={(v) => {
            setData({ records: [], total: 0, page: 1, size: 20 })
            setParams(v === 'all' ? { view: 'all' } : {})
            setFilters({ page: 1, size: 20 })
            setSearch('')
          }}
          items={[
            { key: 'my', label: '我的领用' },
            ...(manage ? [{ key: 'all', label: '全部领用' }] : []),
          ]}
        />
        <div className="stock-filters issue-filters">
          <Input.Search
            aria-label="搜索领用单或材料"
            placeholder="领用单号 / 材料名称或编码 / 项目号"
            value={search}
            allowClear
            onChange={(e) => {
              setSearch(e.target.value)
              if (!e.target.value) filter({ keyword: undefined })
            }}
            onSearch={() => filter({ keyword: search.trim() || undefined })}
          />
          <DatePicker.RangePicker
            aria-label="领用日期范围"
            onChange={(d) =>
              filter({
                from: d?.[0]?.format('YYYY-MM-DD'),
                to: d?.[1]?.format('YYYY-MM-DD'),
              })
            }
          />
          {scope === 'all' && (
            <>
              <Input
                aria-label="筛选领用人"
                placeholder="领用人姓名 / 用户名"
                value={filters.user || ''}
                maxLength={100}
                onChange={(e) =>
                  filter({ user: e.target.value.trim() || undefined })
                }
              />
              <Input
                aria-label="筛选项目编号"
                placeholder="精确项目编号"
                value={filters.projectNo || ''}
                maxLength={100}
                onChange={(e) =>
                  filter({ projectNo: e.target.value.trim() || undefined })
                }
              />
            </>
          )}
        </div>
        {error && (
          <Alert
            type="error"
            message="领用记录加载失败"
            description={error}
            action={
              <Button onClick={() => setRevision((r) => r + 1)}>重试</Button>
            }
          />
        )}
        {screens.md ? (
          <Table<IssueOrder>
            rowKey="id"
            size="middle"
            loading={loading}
            dataSource={data.records}
            scroll={{ x: 1250 }}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无符合条件的领用记录"
                />
              ),
            }}
            pagination={pagination}
            columns={[
              {
                title: '领用单号 / 材料',
                width: 250,
                render: (_, r) => (
                  <button
                    className="material-name-link"
                    onClick={() => details(r.id)}
                  >
                    <span className="material-cell-stack">
                      <strong>{r.issueNo}</strong>
                      <small>{r.materialNames}</small>
                    </span>
                  </button>
                ),
              },
              {
                title: '批次 / 数量',
                width: 210,
                render: (_, r) => (
                  <div className="material-cell-stack">
                    <span>{r.batchCodes}</span>
                    <small>{r.quantitySummary}</small>
                  </div>
                ),
              },
              { title: '领用人', width: 120, dataIndex: 'recipientName' },
              {
                title: '项目 / 用途',
                width: 200,
                render: (_, r) => (
                  <div className="material-cell-stack">
                    <span>{r.projectNo}</span>
                    <small>{r.usage}</small>
                  </div>
                ),
              },
              {
                title: '金额',
                width: 150,
                render: (_, r) => money(r.totalAmount, r.currency),
              },
              {
                title: '时间',
                width: 160,
                render: (_, r) => dateTime(r.createdAt),
              },
              {
                title: '状态',
                width: 90,
                render: () => <Tag color="success">已完成</Tag>,
              },
              {
                title: '操作',
                fixed: 'right',
                width: 180,
                render: (_, r) => (
                  <Space size={0}>
                    <Button type="link" onClick={() => details(r.id)}>
                      详情
                    </Button>
                    <Button
                      type="text"
                      aria-label={'下载凭证 ' + r.issueNo}
                      icon={<DownloadOutlined />}
                      loading={downloading === r.id}
                      onClick={() => void download(r)}
                    >
                      PDF
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        ) : (
          <Spin spinning={loading}>
            <div className="issue-records">
              {!data.records.length ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无符合条件的领用记录"
                />
              ) : (
                data.records.map((r) => (
                  <article className="issue-record-card" key={r.id}>
                    <div className="issue-record-heading">
                      <strong>{r.issueNo}</strong>
                      <Tag color="success">已完成</Tag>
                    </div>
                    <h3>{r.materialNames}</h3>
                    <p>{r.batchCodes}</p>
                    <dl>
                      <div>
                        <dt>数量</dt>
                        <dd>{r.quantitySummary}</dd>
                      </div>
                      <div>
                        <dt>领用人</dt>
                        <dd>{r.recipientName}</dd>
                      </div>
                      <div>
                        <dt>项目</dt>
                        <dd>{r.projectNo}</dd>
                      </div>
                      <div>
                        <dt>用途</dt>
                        <dd>{r.usage}</dd>
                      </div>
                      <div>
                        <dt>金额</dt>
                        <dd>{money(r.totalAmount, r.currency)}</dd>
                      </div>
                    </dl>
                    <time>{dateTime(r.createdAt)}</time>
                    <div className="issue-record-actions">
                      <Button onClick={() => details(r.id)}>查看详情</Button>
                      <Button
                        icon={<DownloadOutlined />}
                        loading={downloading === r.id}
                        onClick={() => void download(r)}
                      >
                        PDF 凭证
                      </Button>
                    </div>
                  </article>
                ))
              )}
            </div>
            <Pagination
              {...pagination}
              simple
              size="small"
              className="issue-mobile-pagination"
            />
          </Spin>
        )}
        <p className="stock-hint">
          领用成功后即时扣减库存。个人月度额度由后端按业务时区计算，所有批次共享。
        </p>
      </section>
      {creating && (
        <IssueCreator
          code={code}
          onClose={() => {
            setCreating(false)
            if (code) setParams(scope === 'all' ? { view: 'all' } : {})
          }}
          onSaved={(d) => {
            setCreating(false)
            setFilters({ page: 1, size: 20 })
            setSearch('')
            setRevision((r) => r + 1)
            setParams({ order: String(d.order.id) })
          }}
        />
      )}
      {selected !== undefined && (
        <IssueDetailDrawer
          id={selected}
          onClose={() => setParams(scope === 'all' ? { view: 'all' } : {})}
        />
      )}
    </div>
  )
}
