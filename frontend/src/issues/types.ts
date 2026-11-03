import { requestBlob } from '../api'
export interface IssueOrder {
  id: number
  issueNo: string
  userId: number
  accountName: string
  recipientName: string
  projectNo: string
  course?: string
  usage: string
  remark?: string
  status: string
  currency: string
  totalAmount: string
  createdAt: string
  monthStart: string
  businessZone: string
  lineCount: number
  materialNames: string
  batchCodes: string
  quantitySummary: string
}
export interface IssueLine {
  id: number
  materialId: number
  batchId: number
  materialCode: string
  materialName: string
  spec?: string
  unit: string
  batchCode: string
  returnedQuantity: string
  returnableQuantity: string
  quantity: string
  unitPriceSnapshot: string
  amount: string
  transactionNo: string
  beforeQuantity: string
  afterQuantity: string
}
export interface IssueDetail {
  order: IssueOrder
  lines: IssueLine[]
}
export interface Quota {
  materialId: number
  monthStart: string
  businessZone: string
  monthlyIssueLimit?: string
  usedQuantity: string
  remainingQuantity?: string
}
export async function downloadIssue(id: number, issueNo: string) {
  const blob = await requestBlob('/api/issues/' + id + '/pdf')
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = issueNo + '.pdf'
  a.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1500)
}
