export interface Page<T> {
  records: T[]
  total: number
  page: number
  size: number
}
export interface Stock {
  itemId: number
  materialCode: string
  name: string
  brand?: string
  model?: string
  categoryPath: string
  unit: string
  safeStock: string
  quantity: string
  batchCount: number
  lastReceivedAt?: string
  stockStatus: string
}
export interface Batch {
  id: number
  itemId: number
  materialStatus: string
  batchCode: string
  initialQuantity: string
  quantity: string
  unitPriceSnapshot: string
  currency: string
  itemCode: string
  itemName: string
  spec?: string
  unit: string
  receivedAt: string
  expireDate?: string
  location?: string
  remark?: string
  operatorName: string
  orderId: number
  orderNo: string
  qrContent?: string
}
export interface Order {
  id: number
  orderNo: string
  operatorName: string
  remark?: string
  receivedAt: string
  createdAt: string
  lineCount: number
  status: string
}
export interface Money {
  currency: string
  amount: string
}
export interface OrderDetail {
  order: Order
  lines: Batch[]
  totals: Money[]
}
export interface ItemBatches {
  material: Stock
  batches: Page<Batch>
}
export interface Summary {
  materialCount: number
  stockedMaterialCount: number
  lowStockCount: number
  values: Money[]
}
export interface Transaction {
  id: number
  transactionNo: string
  itemId: number
  itemName: string
  itemCode: string
  batchCode: string
  txnType: string
  qty: string
  beforeQuantity: string
  afterQuantity: string
  unit: string
  operatorName: string
  orderId: number
  referenceType: string
  referenceNo: string
  createdAt: string
}
export function query(values: object) {
  const q = new URLSearchParams()
  Object.entries(values).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v))
  })
  return '?' + q.toString()
}
export const stockNames: Record<string, string> = {
  ZERO: '零库存',
  LOW: '低库存',
  NORMAL: '库存充足',
}
export const txnNames: Record<string, string> = {
  IN: '入库',
  OUT: '领用',
  RETURN: '归还',
  SCRAP: '报废',
}

