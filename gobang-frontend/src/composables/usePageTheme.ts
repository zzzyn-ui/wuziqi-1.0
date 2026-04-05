// 页面主题配置
export interface PageTheme {
  // 头部渐变颜色
  gradientFrom: string
  gradientTo: string
  // 主题色
  primaryColor: string
  primaryLight: string
  // 背景色
  bgPage: string
  bgCard: string
  // 粒子颜色
  particleColor: string
}

export const pageThemes: Record<string, PageTheme> = {
  // 登录页 - 橙色系
  login: {
    gradientFrom: '#ff6b35',
    gradientTo: '#ff8c61',
    primaryColor: '#ff6b35',
    primaryLight: '#ff8c61',
    bgPage: 'linear-gradient(135deg, #fff5e6 0%, #ffe8cc 50%, #ffd9a8 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(255, 107, 53, 0.3)'
  },

  // 注册页 - 橙色系
  register: {
    gradientFrom: '#ff6b35',
    gradientTo: '#ff8c61',
    primaryColor: '#ff6b35',
    primaryLight: '#ff8c61',
    bgPage: 'linear-gradient(135deg, #fff5e6 0%, #ffe8cc 50%, #ffd9a8 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(255, 107, 53, 0.3)'
  },

  // 大厅 - 紫蓝色系
  home: {
    gradientFrom: '#667eea',
    gradientTo: '#764ba2',
    primaryColor: '#667eea',
    primaryLight: '#764ba2',
    bgPage: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(102, 126, 234, 0.3)'
  },

  // 匹配页面 - 绿色系
  match: {
    gradientFrom: '#11998e',
    gradientTo: '#38ef7d',
    primaryColor: '#11998e',
    primaryLight: '#38ef7d',
    bgPage: 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(17, 153, 142, 0.3)'
  },

  // 游戏页面 - 蓝色系
  game: {
    gradientFrom: '#4facfe',
    gradientTo: '#00f2fe',
    primaryColor: '#4facfe',
    primaryLight: '#00f2fe',
    bgPage: 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(79, 172, 254, 0.3)'
  },

  // 人机对战 - 粉紫色系
  pve: {
    gradientFrom: '#f093fb',
    gradientTo: '#f5576c',
    primaryColor: '#f093fb',
    primaryLight: '#f5576c',
    bgPage: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(240, 147, 251, 0.3)'
  },

  // 排行榜 - 金色系
  rank: {
    gradientFrom: '#ffd700',
    gradientTo: '#ffed4e',
    primaryColor: '#ffd700',
    primaryLight: '#ffed4e',
    bgPage: 'linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(255, 215, 0, 0.3)'
  },

  // 对局记录 - 青绿色系
  records: {
    gradientFrom: '#a8edea',
    gradientTo: '#fed6e3',
    primaryColor: '#a8edea',
    primaryLight: '#fed6e3',
    bgPage: 'linear-gradient(135deg, #e0f7fa 0%, #b2ebf2 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(168, 237, 234, 0.3)'
  },

  // 个人资料 - 橙色系
  profile: {
    gradientFrom: '#ff9a8b',
    gradientTo: '#ffc3a0',
    primaryColor: '#ff9a8b',
    primaryLight: '#ffc3a0',
    bgPage: 'linear-gradient(135deg, #ffe6d3 0%, #ffd3b6 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(255, 154, 139, 0.3)'
  },

  // 残局挑战 - 红粉色系
  puzzle: {
    gradientFrom: '#f093fb',
    gradientTo: '#f5576c',
    primaryColor: '#f093fb',
    primaryLight: '#f5576c',
    bgPage: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(240, 147, 251, 0.3)'
  },

  // 复盘 - 蓝紫色系
  replay: {
    gradientFrom: '#a18cd1',
    gradientTo: '#fbc2eb',
    primaryColor: '#a18cd1',
    primaryLight: '#fbc2eb',
    bgPage: 'linear-gradient(135deg, #e8f5e8 0%, #d4fc79 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(161, 140, 209, 0.3)'
  },

  // 好友系统 - 紫色系
  friends: {
    gradientFrom: '#667eea',
    gradientTo: '#764ba2',
    primaryColor: '#667eea',
    primaryLight: '#764ba2',
    bgPage: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(102, 126, 234, 0.3)'
  },

  // 帮助中心 - 橙黄色系
  help: {
    gradientFrom: '#ffecd2',
    gradientTo: '#fcb69f',
    primaryColor: '#fcb69f',
    primaryLight: '#ffecd2',
    bgPage: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(252, 182, 159, 0.3)'
  },

  // 设置 - 灰色系
  settings: {
    gradientFrom: '#868f96',
    gradientTo: '#596164',
    primaryColor: '#868f96',
    primaryLight: '#596164',
    bgPage: 'linear-gradient(135deg, #e0e0e0 0%, #c0c0c0 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(134, 143, 150, 0.3)'
  },

  // 房间 - 紫粉色系
  room: {
    gradientFrom: '#f093fb',
    gradientTo: '#f5576c',
    primaryColor: '#f093fb',
    primaryLight: '#f5576c',
    bgPage: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
    bgCard: '#ffffff',
    particleColor: 'rgba(240, 147, 251, 0.3)'
  }
}

// 获取页面主题
export function getPageTheme(pageName: string): PageTheme {
  return pageThemes[pageName] || pageThemes.home
}

// 应用页面主题到CSS变量
export function applyPageTheme(theme: PageTheme) {
  const root = document.documentElement
  root.style.setProperty('--primary-color', theme.primaryColor)
  root.style.setProperty('--primary-light', theme.primaryLight)
  root.style.setProperty('--primary-gradient', `linear-gradient(135deg, ${theme.gradientFrom} 0%, ${theme.gradientTo} 100%)`)
}
