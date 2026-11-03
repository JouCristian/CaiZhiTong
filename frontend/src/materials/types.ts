export interface Category {
  id: number; parentId?: number; name: string; description?: string; sortOrder: number
  createdAt: string; updatedAt: string; materialCount: number; children: Category[]
}
export type MaterialStatus = 'ACTIVE' | 'DISABLED' | 'ARCHIVED'
export interface Material {
  id: number; categoryId: number; categoryPath: string; materialCode: string; name: string
  brand?: string; model?: string; spec?: string; unit: string; unitPrice: string
  currency: string; safeStock: string; monthlyIssueLimit?: string; status: MaterialStatus; description?: string
  createdAt: string; updatedAt: string
}
export interface MaterialInput {
  categoryId: number; name: string; brand?: string; model?: string; spec?: string; unit: string
  unitPrice: string; currency: string; safeStock: string; status: 'ACTIVE' | 'DISABLED'; description?: string
}
export interface Facet { categoryId: number; brand?: string; model?: string; materialCount: number }
export interface MaterialPage { records: Material[]; total: number; page: number; size: number }
export function flattenCategories(tree: Category[]): Category[] { return tree.flatMap(c => [c, ...flattenCategories(c.children)]) }
export function categoryOptions(tree: Category[]): { value: number; title: string; children: ReturnType<typeof categoryOptions> }[] {
  return tree.map(c => ({ value: c.id, title: c.name, children: categoryOptions(c.children) }))
}
export const statusNames: Record<MaterialStatus, string> = { ACTIVE: '启用', DISABLED: '停用', ARCHIVED: '已归档' }
export function dateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
export function money(value: string, currency: string) {
  const [whole, fraction] = value.split('.')
  return currency + ' ' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? '.' + fraction : '')
}
export function quantity(value: string) { return value.includes('.') ? value.replace(/0+$/, '').replace(/\.$/, '') : value }


