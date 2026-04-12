import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/home',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: {
      title: '登录',
      requiresAuth: false,
    },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue'),
    meta: {
      title: '注册',
      requiresAuth: false,
    },
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: {
      title: '大厅',
      requiresAuth: false, // 首页公开访问，残局等功能不需要登录
    },
  },
  {
    path: '/match',
    name: 'Match',
    component: () => import('@/views/MatchView.vue'),
    meta: {
      title: '匹配',
      requiresAuth: true,
    },
  },
  {
    path: '/room',
    name: 'Room',
    component: () => import('@/views/RoomView.vue'),
    meta: {
      title: '房间',
      requiresAuth: true,
    },
  },
  {
    path: '/game/:roomId',
    name: 'Game',
    component: () => import('@/views/GameView.vue'),
    meta: {
      title: '游戏',
      requiresAuth: true,
    },
    props: true,
  },
  {
    path: '/pve',
    name: 'PVE',
    component: () => import('@/views/PVEView.vue'),
    meta: {
      title: '人机对战',
      requiresAuth: true,
    },
  },
  {
    path: '/rank',
    name: 'Rank',
    component: () => import('@/views/RankView.vue'),
    meta: {
      title: '排行榜',
      requiresAuth: true,
    },
  },
  {
    path: '/records',
    name: 'Records',
    component: () => import('@/views/RecordView.vue'),
    meta: {
      title: '对局记录',
      requiresAuth: true,
    },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: {
      title: '个人资料',
      requiresAuth: true,
    },
  },
  {
    path: '/puzzle',
    name: 'Puzzle',
    component: () => import('@/views/PuzzleView.vue'),
    meta: {
      title: '残局挑战',
      requiresAuth: false, // 残局挑战不需要登录
    },
  },
  {
    path: '/replay/:id?',
    name: 'Replay',
    component: () => import('@/views/ReplayView.vue'),
    meta: {
      title: '对局复盘',
      requiresAuth: true,
    },
    props: true,
  },
  {
    path: '/friends',
    name: 'Friends',
    component: () => import('@/views/FriendsView.vue'),
    meta: {
      title: '好友系统',
      requiresAuth: true,
    },
  },
  {
    path: '/observer/:roomId',
    name: 'Observer',
    component: () => import('@/views/ObserverView.vue'),
    meta: {
      title: '观战',
      requiresAuth: true,
    },
    props: true,
  },
  {
    path: '/help',
    name: 'Help',
    component: () => import('@/views/HelpView.vue'),
    meta: {
      title: '帮助中心',
      requiresAuth: false,
    },
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: {
      title: '设置',
      requiresAuth: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: {
      title: '404',
      requiresAuth: false,
    },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// 路由守卫
router.beforeEach(async (to, _from) => {
  const userStore = useUserStore()

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 五子棋在线对战`
  }

  // 如果需要认证，先尝试恢复用户信息
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    // 尝试从 localStorage 恢复用户信息
    await userStore.initUserInfo()
  }

  const isLoggedIn = userStore.isLoggedIn

  // 检查是否需要认证
  if (to.meta.requiresAuth && !isLoggedIn) {
    // 未登录，跳转到登录页
    console.log('[Router] 未登录，跳转到登录页')
    return {
      name: 'Login',
      query: { redirect: to.fullPath },
    }
  }

  if ((to.name === 'Login' || to.name === 'Register') && isLoggedIn) {
    // 已登录，跳转到首页
    console.log('[Router] 已登录，跳转到首页')
    return { name: 'Home' }
  }

  return true
})

// 路由后置守卫
router.afterEach((to, from) => {
  console.log(`[Router] Navigate from ${String(from.name)} to ${String(to.name)}`)
})

export default router
