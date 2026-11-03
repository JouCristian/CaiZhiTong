import ReactDOM from 'react-dom/client'
import { App as AntApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './auth'
import { App } from './App'
import { SystemProvider } from './system'
import './styles.css'
ReactDOM.createRoot(document.getElementById('root')!).render(
  <ConfigProvider locale={zhCN} theme={{
    token: {
      colorPrimary: '#0066d6', colorInfo: '#0066d6', colorText: '#202329',
      colorTextSecondary: '#656b75', colorBorder: '#dce0e6', colorBgLayout: '#f5f6f8',
      borderRadius: 10, controlHeight: 42, fontSize: 14,
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
      boxShadow: '0 4px 8px rgba(25, 36, 53, .08)',
    },
    components: {
      Button: { primaryShadow: 'none', fontWeight: 500 },
      Menu: { itemHeight: 42, itemBorderRadius: 9, itemSelectedBg: '#e7effb', itemSelectedColor: '#005bc2', iconSize: 18 },
      Input: { activeShadow: '0 0 0 3px rgba(0,102,214,.10)' },
      Popover: { motion: false },
    },
  }}>
    <AntApp><BrowserRouter><SystemProvider><AuthProvider><App /></AuthProvider></SystemProvider></BrowserRouter></AntApp>
  </ConfigProvider>,
)

