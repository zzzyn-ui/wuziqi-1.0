// 全局变量类型声明
declare const __APP_VERSION__: string
declare const __API_BASE_URL__: string

// 扩展 Window 接口
interface Window {
  __APP_VERSION__?: string
  __API_BASE_URL__?: string
}

export {}
