import { MotionDrawer as Drawer } from '../motion/MotionDrawer'
import { useEffect, useState } from 'react'
import { Alert, App, Button, Descriptions, Skeleton, Tag } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { request } from '../api'
import { useAuth } from '../auth'
import { dateTime, money, quantity } from '../materials/types'
import { downloadIssue } from './types'
import type { IssueDetail } from './types'
export function IssueDetailDrawer({
  id,
  onClose,
}: {
  id: number
  onClose: () => void
}) {
  const { user } = useAuth(),
    { message } = App.useApp(),
    [data, setData] = useState<IssueDetail>(),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0),
    [downloading, setDownloading] = useState(false)
  useEffect(() => {
    let active = true
    setData(undefined)
    setError('')
    request<IssueDetail>('/api/issues/' + id)
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
  async function download() {
    if (!data) return
    setDownloading(true)
    try {
      await downloadIssue(id, data.order.issueNo)
    } catch (e) {
      void message.error((e as Error).message)
    } finally {
      setDownloading(false)
    }
  }
  return (
    <Drawer
      title="领用单详情"
      open
      width="min(800px,100vw)"
      onClose={onClose}
      footer={
        <div className="drawer-actions">
          <Button onClick={onClose}>关闭详情</Button>
          {data && (
            <Button
              type="primary"
              loading={downloading}
              icon={<DownloadOutlined />}
              onClick={() => void download()}
            >
              下载 PDF 凭证
            </Button>
          )}
        </div>
      }
    >
      {error ? (
        <Alert
          type="error"
          showIcon
          message="无法读取领用单"
          description={error}
          action={<Button onClick={() => setRetry((r) => r + 1)}>重试</Button>}
        />
      ) : !data ? (
        <Skeleton active paragraph={{ rows: 7 }} />
      ) : (
        <>
          <div className="stock-detail-title">
            <div>
              <h2>{data.order.issueNo}</h2>
              <p>
                {data.order.recipientName} · {dateTime(data.order.createdAt)}
              </p>
            </div>
            <Tag color="success">已完成</Tag>
          </div>
          <Descriptions
            column={{ xs: 1, sm: 2 }}
            items={[
              {
                key: 'user',
                label: '领用人',
                children:
                  data.order.recipientName + ' / ' + data.order.accountName,
              },
              {
                key: 'project',
                label: '项目编号',
                children: data.order.projectNo,
              },
              {
                key: 'course', label: '课程', children: data.order.course || '未关联课程',
              },
              {
                key: 'usage',
                label: '用途',
                span: 2,
                children: (
                  <span className="preserve-lines">{data.order.usage}</span>
                ),
              },
              {
                key: 'remark',
                label: '备注',
                span: 2,
                children: (
                  <span className="preserve-lines">
                    {data.order.remark || '未填写'}
                  </span>
                ),
              },
              {
                key: 'month',
                label: '计入月份',
                children: data.order.monthStart.slice(0, 7),
              },
              {
                key: 'zone',
                label: '业务时区',
                children: data.order.businessZone,
              },
            ]}
          />
          <h3 className="issue-lines-title">领用明细与库存流水</h3>
          <div className="issue-lines">
            {data.lines.map((l) => (
              <article className="issue-detail-line" key={l.id}>
                <div className="issue-line-heading">
                  <div>
                    <h3>{l.materialName}</h3>
                    <small>{l.materialCode}</small>
                  </div>
                  <strong>{money(l.amount, data.order.currency)}</strong>
                </div>
                <p className="stock-hint">{l.spec || '未填写规格'}</p>
                <Descriptions
                  column={{ xs: 1, sm: 2 }}
                  size="small"
                  items={[
                    {
                      key: 'batch',
                      label: '关联批次',
                      span: 2,
                      children: (
                        <Link to={'/stock/batches/' + l.batchCode}>
                          {l.batchCode}
                        </Link>
                      ),
                    },
                    {
                      key: 'qty',
                      label: '领用数量',
                      children: quantity(l.quantity) + ' ' + l.unit,
                    },
                    {
                      key: 'price',
                      label: '单价快照',
                      children: money(l.unitPriceSnapshot, data.order.currency),
                    },
                    {
                      key: 'balance',
                      label: '批次余额',
                      children:
                        quantity(l.beforeQuantity) +
                        ' → ' +
                        quantity(l.afterQuantity) +
                        ' ' +
                        l.unit,
                    },
                    { key: 'tx', label: 'OUT 流水', children: l.transactionNo },
                  ]}
                />
                {user?.modules.includes('inventory') && (
                  <Link
                    to={'/inventory?view=transactions&batch=' + l.batchCode}
                  >
                    查看关联库存流水
                  </Link>
                )}
              </article>
            ))}
          </div>
          <section className="issue-amount">
            <span>领用总金额</span>
            <strong>
              {money(data.order.totalAmount, data.order.currency)}
            </strong>
            <small>按已保存的批次价格快照计价，历史记录不可直接编辑。</small>
          </section>
        </>
      )}
    </Drawer>
  )
}
