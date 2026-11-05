import { MotionDrawer as Drawer, DrawerPresence } from '../motion/MotionDrawer'
import { useAuth } from '../auth'
import Decimal from 'decimal.js'
import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Empty, Modal, Result, Skeleton, Space, Table, Tag } from 'antd'
import {
  DownloadOutlined,
  PrinterOutlined,
  QrcodeOutlined,
} from '@ant-design/icons'
import { Link, useParams } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { request, requestBlob, ApiError } from '../api'
import { dateTime, money, quantity } from '../materials/types'
import type { Batch, ItemBatches } from './types'
import './stock.css'
export function Label({
  batch,
  onClose,
}: {
  batch: Batch
  onClose: () => void
}) {
  const [url, setUrl] = useState(''),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true,
      objectUrl = ''
    setError('')
    setUrl('')
    requestBlob('/api/stock-batches/' + batch.batchCode + '/qrcode')
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob)
        if (active) setUrl(objectUrl)
        else URL.revokeObjectURL(objectUrl)
      })
      .catch((e) => {
        if (active) setError((e as Error).message)
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [batch.batchCode, retry])
  const label = (
    <div className="batch-label">
      <div className="label-brand">材智通 · 材料批次</div>
      <h2>{batch.itemName}</h2>
      <p>{batch.spec || '规格未填写'}</p>
      {url ? (
        <img
          src={url}
          width="240"
          height="240"
          alt={'批次二维码 ' + batch.batchCode}
        />
      ) : (
        <Skeleton.Image active />
      )}
      <strong>{batch.batchCode}</strong>
      <small>
        {batch.itemCode} · {batch.location || '库位未填写'}
      </small>
    </div>
  )
  return (
    <Modal
      title="批次二维码与标签"
      open
      onCancel={onClose}
      footer={
        <Space>
          <Button
            disabled={!url}
            icon={<DownloadOutlined />}
            href={url || undefined}
            download={batch.batchCode + '.png'}
          >
            下载 PNG
          </Button>
          <Button
            type="primary"
            disabled={!url}
            icon={<PrinterOutlined />}
            onClick={() => window.print()}
          >
            打印标签
          </Button>
        </Space>
      }
    >
      {error && (
        <Alert
          type="error"
          message={error}
          action={<Button onClick={() => setRetry((r) => r + 1)}>重试</Button>}
        />
      )}
      {label}
      <p className="stock-hint">
        扫码将打开批次详情；受保护信息需要登录后查看。
      </p>
      {createPortal(<div className="print-label">{label}</div>, document.body)}
    </Modal>
  )
}
export function BatchInfo({ batch }: { batch: Batch }) {
  const { user } = useAuth()
  const [label, setLabel] = useState(false)
  return (
    <>
      <div className="stock-detail-title">
        <div>
          <h2>{batch.itemName}</h2>
          <p>{batch.batchCode}</p>
        </div>
        <div className="batch-issue-actions">
          {user?.modules.includes('requisitions') &&
            (batch.materialStatus === 'ACTIVE' &&
            new Decimal(batch.quantity).gt(0) ? (
              <Link to={'/requisitions?batch=' + batch.batchCode}>
                <Button type="primary">领用此批次</Button>
              </Link>
            ) : (
              <Button disabled>当前批次不可领用</Button>
            ))}
          <Button icon={<QrcodeOutlined />} onClick={() => setLabel(true)}>
            二维码标签
          </Button>
        </div>
      </div>
      <Descriptions
        column={1}
        items={[
          { key: 'code', label: '材料编码', children: batch.itemCode },
          { key: 'spec', label: '规格', children: batch.spec || '未填写' },
          {
            key: 'qty',
            label: '剩余 / 原始数量',
            children:
              quantity(batch.quantity) +
              ' / ' +
              quantity(batch.initialQuantity) +
              ' ' +
              batch.unit,
          },
          {
            key: 'price',
            label: '入库单价',
            children: money(batch.unitPriceSnapshot, batch.currency),
          },
          {
            key: 'date',
            label: '入库时间',
            children: dateTime(batch.receivedAt),
          },
          {
            key: 'expiry',
            label: '有效期',
            children: batch.expireDate || '未设置',
          },
          {
            key: 'location',
            label: '库位',
            children: batch.location || '未填写',
          },
          { key: 'order', label: '关联入库单', children: batch.orderNo },
          { key: 'operator', label: '经办人', children: batch.operatorName },
          { key: 'remark', label: '备注', children: batch.remark || '未填写' },
        ]}
      />
      {label && <Label batch={batch} onClose={() => setLabel(false)} />}
    </>
  )
}
export function BatchList({
  data,
  original = false,
}: {
  data: Batch[]
  original?: boolean
}) {
  return (
    <Table<Batch>
      rowKey="id"
      size="small"
      dataSource={data}
      pagination={false}
      scroll={{ x: 1100 }}
      locale={{
        emptyText: (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无入库批次"
          />
        ),
      }}
      columns={[
        {
          title: '材料 / 规格',
          width: 180,
          render: (_, b) => (
            <div className="material-cell-stack">
              <strong>{b.itemName}</strong>
              <small>{b.spec || b.itemCode}</small>
            </div>
          ),
        },
        {
          title: '批次 / 入库单',
          width: 230,
          render: (_, b) => (
            <div className="material-cell-stack">
              <Link to={'/stock/batches/' + b.batchCode}>{b.batchCode}</Link>
              <small>{b.orderNo}</small>
            </div>
          ),
        },
        {
          title: original ? '入库数量' : '剩余数量',
          render: (_, b) =>
            quantity(original ? b.initialQuantity : b.quantity) + ' ' + b.unit,
        },
        {
          title: '入库单价',
          render: (_, b) => money(b.unitPriceSnapshot, b.currency),
        },
        {
          title: '库位 / 有效期',
          render: (_, b) => (
            <div>
              {b.location || '—'}
              <small className="stock-block">
                {b.expireDate || '无有效期'}
              </small>
            </div>
          ),
        },
        { title: '入库时间', render: (_, b) => dateTime(b.receivedAt) },
      ]}
    />
  )
}
export function StockPeek({ itemId }: { itemId: number }) {
  const [data, setData] = useState<ItemBatches>(),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0),
    [open, setOpen] = useState(false)
  useEffect(() => {
    let active = true
    setError('')
    request<ItemBatches>('/api/stocks/' + itemId + '/batches?size=100')
      .then((d) => {
        if (active) setData(d)
      })
      .catch((e) => {
        if (active) setError((e as Error).message)
      })
    return () => {
      active = false
    }
  }, [itemId, retry])
  return (
    <div className="stock-peek">
      {error ? (
        <Alert
          type="error"
          message={error}
          action={<Button onClick={() => setRetry((r) => r + 1)}>重试</Button>}
        />
      ) : data ? (
        <>
          <span>
            当前库存{' '}
            <strong>
              {quantity(data.material.quantity)} {data.material.unit}
            </strong>
          </span>
          <Button type="link" onClick={() => setOpen(true)}>
            查看 {data.batches.total} 个批次
          </Button>
        </>
      ) : (
        <Skeleton.Input active />
      )}
      <DrawerPresence>{open && <StockDrawer itemId={itemId} onClose={() => setOpen(false)} />}</DrawerPresence>
    </div>
  )
}
export function StockDrawer({
  itemId,
  onClose,
}: {
  itemId: number
  onClose: () => void
}) {
  const [data, setData] = useState<ItemBatches>(),
    [page, setPage] = useState(1),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    request<ItemBatches>('/api/stocks/' + itemId + '/batches?page=' + page)
      .then((d) => {
        if (active) setData(d)
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
  }, [itemId, page, retry])
  return (
    <Drawer title="库存与批次" open width="min(960px,100vw)" onClose={onClose}>
      {error ? (
        <Alert
          type="error"
          message={error}
          action={<Button onClick={() => setRetry((r) => r + 1)}>重试</Button>}
        />
      ) : loading ? (
        <Skeleton active />
      ) : (
        data && (
          <>
            <div className="stock-detail-title">
              <div>
                <h2>{data.material.name}</h2>
                <p>
                  {data.material.materialCode} · {data.material.categoryPath}
                </p>
              </div>
              <Tag>
                {quantity(data.material.quantity)} {data.material.unit}
              </Tag>
            </div>
            <p className="stock-hint">
              安全库存 {quantity(data.material.safeStock)} {data.material.unit}{' '}
              · 共 {data.batches.total} 个批次
            </p>
            <BatchList data={data.batches.records} />
            <div className="stock-pager">
              <Button
                disabled={page === 1}
                onClick={() => setPage((p) => p - 1)}
              >
                上一页
              </Button>
              <span>第 {page} 页</span>
              <Button
                disabled={page * 20 >= data.batches.total}
                onClick={() => setPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </>
        )
      )}
    </Drawer>
  )
}
export function BatchPage() {
  const { code } = useParams(),
    [batch, setBatch] = useState<Batch>(),
    [error, setError] = useState(''),
    [missing, setMissing] = useState(false),
    [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setError('')
    setMissing(false)
    setBatch(undefined)
    request<Batch>('/api/stock-batches/' + encodeURIComponent(code || ''))
      .then((d) => {
        if (active) setBatch(d)
      })
      .catch((e) => {
        if (active) {
          setError((e as Error).message)
          setMissing(e instanceof ApiError && e.status === 404)
        }
      })
    return () => {
      active = false
    }
  }, [code, retry])
  return (
    <div className="page-enter">
      <div className="page-heading">
        <div>
          <h1>批次详情</h1>
          <p>每一份材料，都有清晰的来处。</p>
        </div>
        <Link to="/materials">返回材料库</Link>
      </div>
      <section className="surface stock-detail">
        {error ? (
          <Result
            status={missing ? '404' : 'warning'}
            title={missing ? '库存批次不存在' : '暂时无法读取批次'}
            subTitle={error}
            extra={
              !missing && (
                <Button onClick={() => setRetry((r) => r + 1)}>重试</Button>
              )
            }
          />
        ) : batch ? (
          <BatchInfo batch={batch} />
        ) : (
          <Skeleton active />
        )}
      </section>
    </div>
  )
}
