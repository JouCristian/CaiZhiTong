import {
  AppstoreOutlined, DatabaseOutlined, InboxOutlined, ImportOutlined,
  ExportOutlined, RetweetOutlined, BellOutlined, BarChartOutlined,
  TeamOutlined, SettingOutlined,
} from '@ant-design/icons'
export const modules = [
  { key: 'dashboard', title: '工作台', icon: AppstoreOutlined, description: '查看材料库概览与常用入口' },
  { key: 'materials', title: '材料管理', icon: DatabaseOutlined, description: '维护材料档案、分类与规格信息', phase: '材料档案' },
  { key: 'inventory', title: '库存管理', icon: InboxOutlined, description: '掌握材料库存、库位与可用数量', phase: '库存台账' },
  { key: 'inbound', title: '入库管理', icon: ImportOutlined, description: '记录采购到货与材料入库', phase: '入库流程' },
  { key: 'requisitions', title: '领用管理', icon: ExportOutlined, description: '登记材料领用并查询个人记录', phase: '领用流程' },
  { key: 'returns', title: '归还与报废', icon: RetweetOutlined, description: '跟踪材料归还与报废处置', phase: '流转记录' },
  { key: 'alerts', title: '库存预警', icon: BellOutlined, description: '识别低库存与需要关注的材料', phase: '预警规则' },
  { key: 'reports', title: '数据报表', icon: BarChartOutlined, description: '从材料流转中了解使用与库存趋势', phase: '统计分析' },
  { key: 'users', title: '用户与权限', icon: TeamOutlined, description: '管理成员账户与角色分配', phase: '权限管理' },
  { key: 'settings', title: '系统设置', icon: SettingOutlined, description: '配置材料库基础信息与系统偏好', phase: '基础配置' },
  { key: 'demo/dashboard', title: '演示工作台', icon: AppstoreOutlined, description: '独立合成数据概览' },
  { key: 'demo/materials', title: '演示材料', icon: DatabaseOutlined, description: '浏览合成材料档案' },
  { key: 'demo/stocks', title: '演示库存', icon: InboxOutlined, description: '查看示例批次与库存变化' },
]
export const roleNames: Record<string, string> = {
  GUEST: '游客', STUDENT: '学生', LAB_TECH: '实验员', TEACHER: '教师', PURCHASER: '采购员', ADMIN: '系统管理员',
}


