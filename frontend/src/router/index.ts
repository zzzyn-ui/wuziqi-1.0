import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { useUserStore } from "@/store/modules/user";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    redirect: "/home",
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/features/auth/LoginView.vue"),
    meta: { title: "登录", requiresAuth: false },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("@/features/auth/RegisterView.vue"),
    meta: { title: "注册", requiresAuth: false },
  },
  {
    path: "/home",
    name: "Home",
    component: () => import("@/views/HomeView.vue"),
    meta: { title: "大厅", requiresAuth: false },
  },
  {
    path: "/match",
    name: "Match",
    component: () => import("@/features/match/MatchView.vue"),
    meta: { title: "匹配", requiresAuth: true },
  },
  {
    path: "/room",
    name: "Room",
    component: () => import("@/features/room/RoomView.vue"),
    meta: { title: "房间", requiresAuth: true },
  },
  {
    path: "/game/:roomId",
    name: "Game",
    component: () => import("@/features/game/GameView.vue"),
    meta: { title: "游戏", requiresAuth: true },
    props: true,
  },
  {
    path: "/pve",
    name: "PVE",
    component: () => import("@/features/game/PVEView.vue"),
    meta: { title: "人机对战", requiresAuth: true },
  },
  {
    path: "/rank",
    name: "Rank",
    component: () => import("@/features/rank/RankView.vue"),
    meta: { title: "排行榜", requiresAuth: true },
  },
  {
    path: "/records",
    name: "Records",
    component: () => import("@/features/record/RecordsView.vue"),
    meta: { title: "对局记录", requiresAuth: true },
  },
  {
    path: "/profile",
    name: "Profile",
    component: () => import("@/features/profile/ProfileView.vue"),
    meta: { title: "个人资料", requiresAuth: true },
  },
  {
    path: "/puzzle",
    name: "Puzzle",
    component: () => import("@/features/puzzle/PuzzleView.vue"),
    meta: { title: "残局挑战", requiresAuth: false },
  },
  {
    path: "/replay/:id?",
    name: "Replay",
    component: () => import("@/features/record/ReplayView.vue"),
    meta: { title: "对局复盘", requiresAuth: true },
    props: true,
  },
  {
    path: "/friends",
    name: "Friends",
    component: () => import("@/features/friend/FriendsView.vue"),
    meta: { title: "好友系统", requiresAuth: true },
  },
  {
    path: "/observer/:roomId",
    name: "Observer",
    component: () => import("@/features/observer/ObserverView.vue"),
    meta: { title: "观战", requiresAuth: true },
    props: true,
  },
  {
    path: "/help",
    name: "Help",
    component: () => import("@/views/HelpView.vue"),
    meta: { title: "帮助中心", requiresAuth: false },
  },
  {
    path: "/settings",
    name: "Settings",
    component: () => import("@/features/profile/SettingsView.vue"),
    meta: { title: "设置", requiresAuth: true },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/views/NotFoundView.vue"),
    meta: { title: "404", requiresAuth: false },
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach(async (to, _from) => {
  const userStore = useUserStore();

  if (to.meta.title) {
    document.title = `${to.meta.title} - 五子棋在线对战`;
  }

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    await userStore.initUserInfo();
  }

  const isLoggedIn = userStore.isLoggedIn;

  if (to.meta.requiresAuth && !isLoggedIn) {
    return {
      name: "Login",
      query: { redirect: to.fullPath },
    };
  }

  if ((to.name === "Login" || to.name === "Register") && isLoggedIn) {
    return { name: "Home" };
  }

  return true;
});

router.afterEach((to, from) => {
  console.log(`[Router] Navigate from ${String(from.name)} to ${String(to.name)}`);
});

export default router;
