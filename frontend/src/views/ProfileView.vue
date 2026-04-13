<template>
  <div class="profile-view-unified">
    <PageHeader
      title="👤 个人资料"
      subtitle="管理您的个人信息"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <ContentCard>
        <template #default>
          <div v-if="loading" class="loading-wrapper">
            <LoadingState text="加载中..." />
          </div>

          <div v-else class="profile-content">
            <!-- 头像区域 -->
            <div class="avatar-section">
              <div class="avatar-large">
                {{ userStore.userInfo?.nickname?.charAt(0) || userStore.userInfo?.username?.charAt(0) || '?' }}
              </div>
              <ActionButton variant="ghost" size="small">
                更换头像
              </ActionButton>
            </div>

            <!-- 用户信息表单 -->
            <div class="form-section">
              <div class="form-group">
                <label class="form-label">用户名</label>
                <input
                  v-model="profileForm.username"
                  type="text"
                  placeholder="请输入用户名"
                  class="form-input"
                  maxlength="20"
                />
                <div class="form-hint">用户名必须唯一，修改后全局生效</div>
              </div>

              <div class="form-group">
                <label class="form-label">昵称</label>
                <input
                  v-model="profileForm.nickname"
                  type="text"
                  placeholder="请输入昵称"
                  class="form-input"
                  maxlength="20"
                />
                <div class="form-hint">昵称可以与其他用户相同</div>
              </div>

              <div class="form-group">
                <label class="form-label">用户ID</label>
                <div class="form-value">{{ userStore.userInfo?.id }}</div>
              </div>

              <!-- 统计信息 -->
              <div class="stats-section">
                <div class="stat-card">
                  <div class="stat-icon">⭐</div>
                  <div class="stat-info">
                    <div class="stat-label">等级</div>
                    <div class="stat-value">{{ getRankTitle(userStats.rating || userStore.userInfo?.rating || 800) }}</div>
                  </div>
                </div>

                <div class="stat-card">
                  <div class="stat-icon">🏆</div>
                  <div class="stat-info">
                    <div class="stat-label">积分</div>
                    <div class="stat-value">{{ userStats.rating || userStore.userInfo?.rating || 800 }}</div>
                  </div>
                </div>

                <div class="stat-card">
                  <div class="stat-icon">🎮</div>
                  <div class="stat-info">
                    <div class="stat-label">对局</div>
                    <div class="stat-value">{{ userStats.totalGames || 0 }}</div>
                  </div>
                </div>

                <div class="stat-card">
                  <div class="stat-icon">📊</div>
                  <div class="stat-info">
                    <div class="stat-label">胜率</div>
                    <div class="stat-value">{{ userStats.winRate || 0 }}%</div>
                  </div>
                </div>
              </div>

              <!-- 战绩详情 -->
              <div class="record-details">
                <h4>战绩详情</h4>
                <div class="record-items">
                  <div class="record-item win">
                    <span class="record-label">胜利</span>
                    <span class="record-value">{{ userStats.wins || 0 }}</span>
                  </div>
                  <div class="record-item loss">
                    <span class="record-label">失败</span>
                    <span class="record-value">{{ userStats.losses || 0 }}</span>
                  </div>
                  <div class="record-item draw">
                    <span class="record-label">平局</span>
                    <span class="record-value">{{ userStats.draws || 0 }}</span>
                  </div>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="action-buttons">
                <ActionButton
                  variant="primary"
                  :loading="saving"
                  @click="handleSave"
                  class="save-button"
                >
                  保存修改
                </ActionButton>
              </div>

              <div class="back-button-wrapper">
                <ActionButton
                  variant="ghost"
                  size="small"
                  @click="$router.push('/home')"
                >
                  返回大厅
                </ActionButton>
              </div>
            </div>
          </div>
        </template>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { userApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('profile')

// 应用主题
let refreshInterval: number | null = null

onMounted(async () => {
  applyPageTheme(theme)

  // 确保用户信息已加载
  if (!userStore.userInfo && userStore.token) {
    await userStore.initUserInfo()
  }

  // 初始化表单数据
  if (userStore.userInfo) {
    profileForm.username = userStore.userInfo.username || ''
    profileForm.nickname = userStore.userInfo.nickname || userStore.userInfo.username || ''
  }

  fetchUserStats()

  // 启动定时刷新（每30秒刷新一次）
  refreshInterval = window.setInterval(() => {
    fetchUserStats(true)
  }, 30000)

  // 监听窗口聚焦事件
  window.addEventListener('focus', handleWindowFocus)
})

onUnmounted(() => {
  // 清理定时器
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
  window.removeEventListener('focus', handleWindowFocus)
})

// 页面激活时刷新数据
onActivated(() => {
  fetchUserStats(true)
})

// 窗口聚焦时刷新数据
const handleWindowFocus = () => {
  fetchUserStats(true)
}

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)

// 从store获取初始值
const getInitialNickname = () => {
  return userStore.userInfo?.nickname || userStore.userInfo?.username || ''
}

const profileForm = reactive({
  username: userStore.userInfo?.username || '',
  nickname: getInitialNickname()
})

const userStats = ref({
  totalGames: 0,
  wins: 0,
  losses: 0,
  draws: 0,
  winRate: 0,
  level: userStore.userInfo?.level || 1,
  rating: userStore.userInfo?.rating || 800
})

const fetchUserStats = async (silent = false) => {
  if (!userStore.userInfo?.id) return

  if (!silent) {
    loading.value = true
  }
  try {
    const response = await userApi.getUserStats(userStore.userInfo.id)
    if (response && response.success === true) {
      const statsData = response.data || {}
      userStats.value = {
        ...userStats.value,
        ...statsData
      }

      // 同时更新 store 中的用户信息（包括最新的 rating）
      if (userStore.userInfo) {
        userStore.userInfo.rating = statsData.rating || userStats.value.rating
        userStore.userInfo.level = statsData.level || userStats.value.level
        userStore.userInfo.exp = statsData.exp || 0
        // 更新 localStorage
        localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      }
    }
  } catch (error: any) {
    console.error('[ProfileView] 获取用户统计失败:', error)
    // 使用默认值
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

const handleSave = async () => {
  console.log('[ProfileView] ==================== handleSave 开始 ====================')
  console.log('[ProfileView] userStore.userInfo:', userStore.userInfo)
  console.log('[ProfileView] profileForm:', profileForm)

  if (!userStore.userInfo?.id) {
    console.log('[ProfileView] ❌ 用户ID不存在')
    ElMessage.warning('用户未登录')
    return
  }

  // 验证用户名
  if (!profileForm.username || profileForm.username.trim() === '') {
    console.log('[ProfileView] ❌ 用户名为空')
    ElMessage.warning('用户名不能为空')
    return
  }

  // 验证用户名长度
  if (profileForm.username.length < 2 || profileForm.username.length > 20) {
    console.log('[ProfileView] ❌ 用户名长度不符合要求:', profileForm.username.length)
    ElMessage.warning('用户名长度必须在2-20个字符之间')
    return
  }

  console.log('[ProfileView] ✅ 验证通过，准备保存')
  saving.value = true
  try {
    const updateData: any = {}

    // 只有当用户名或昵称发生变化时才发送更新请求
    if (profileForm.username !== userStore.userInfo.username) {
      updateData.username = profileForm.username.trim()
      console.log('[ProfileView] 用户名发生变化:', userStore.userInfo.username, '->', updateData.username)
    }

    if (profileForm.nickname !== (userStore.userInfo.nickname || userStore.userInfo.username)) {
      updateData.nickname = profileForm.nickname.trim()
      console.log('[ProfileView] 昵称发生变化:', userStore.userInfo.nickname || userStore.userInfo.username, '->', updateData.nickname)
    }

    // 如果没有任何变化，提示用户
    if (Object.keys(updateData).length === 0) {
      console.log('[ProfileView] ℹ️ 没有需要保存的修改')
      saving.value = false
      return
    }

    console.log('[ProfileView] 📤 发送更新请求:', updateData)
    // 发送更新请求
    const response = await userApi.updateUserInfo(userStore.userInfo.id, updateData)
    console.log('[ProfileView] 📥 收到响应:', response)

    // 更新本地store
    if (updateData.username !== undefined) {
      userStore.userInfo!.username = updateData.username
    }
    if (updateData.nickname !== undefined) {
      userStore.userInfo!.nickname = updateData.nickname
    }

    // 更新 localStorage
    localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
  } catch (error: any) {
    console.error('[ProfileView] ❌ 保存失败:', error)
    // 显示服务器返回的错误信息
    const errorMessage = error?.response?.data?.message || error?.message || '保存失败'
    ElMessage.error(errorMessage)
  } finally {
    saving.value = false
  }
}

/**
 * 根据积分获取专业称号
 */
const getRankTitle = (rating: number): string => {
  if (rating < 1000) return 'Lv.1 入门棋手'
  if (rating < 1200) return 'Lv.2 初级棋手'
  if (rating < 1400) return 'Lv.3 中级棋手'
  if (rating < 1600) return 'Lv.4 中级棋手'
  if (rating < 1800) return 'Lv.5 高级棋手'
  if (rating < 2000) return 'Lv.6 高级棋手'
  if (rating < 2200) return 'Lv.7 棋士'
  if (rating < 2400) return 'Lv.8 高手'
  if (rating < 2600) return 'Lv.9 大师'
  return 'Lv.10 宗师'
}

// 注意：已移除 watchEffect，因为它会在用户编辑时重置表单
// 表单在 onMounted 中初始化，保存成功后手动更新即可
</script>

<style scoped>
.profile-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
  background: linear-gradient(180deg,
    #ffc9a8 0%,
    #ffb886 15%,
    #ffa764 30%,
    #ff9662 45%,
    #ff8550 60%,
    #ff743e 75%,
    #ff632c 90%,
    #ff521a 100%
  );
  position: relative;
}

/* 让PageHeader背景透明，与页面背景融合 */
.profile-view-unified :deep(.header-background) {
  background: transparent !important;
}

.profile-view-unified :deep(.header-background::after) {
  display: none;
}

.profile-view-unified::before {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    radial-gradient(circle at 30% 40%, rgba(255, 180, 120, 0.35) 0%, transparent 50%),
    radial-gradient(circle at 70% 70%, rgba(255, 150, 90, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 50% 20%, rgba(255, 200, 140, 0.3) 0%, transparent 50%);
  pointer-events: none;
  z-index: 0;
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.page-content-wrapper {
  max-width: 700px;
  margin: 0 auto;
  padding: 0 20px;
  position: relative;
  z-index: 10;
  pointer-events: auto;
}

.loading-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-content {
  padding: 20px 0;
  position: relative;
  z-index: 15;
  pointer-events: auto;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40px;
}

.avatar-large {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff9a6c 0%, #ffb886 50%, #ffd6a8 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: bold;
  margin-bottom: 15px;
  box-shadow: 0 8px 30px rgba(255, 154, 108, 0.4), 0 0 0 4px rgba(255, 255, 255, 0.5);
  animation: avatarPulse 3s ease-in-out infinite;
}

@keyframes avatarPulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 8px 30px rgba(255, 154, 108, 0.4), 0 0 0 4px rgba(255, 255, 255, 0.5);
  }
  50% {
    transform: scale(1.02);
    box-shadow: 0 8px 40px rgba(255, 154, 108, 0.5), 0 0 0 6px rgba(255, 255, 255, 0.4);
  }
}

.form-section {
  max-width: 480px;
  margin: 0 auto;
  text-align: left;
  position: relative;
  z-index: 5;
}

.form-group {
  margin-bottom: 25px;
  position: relative;
  z-index: 10;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #444;
  margin-bottom: 8px;
}

.form-value {
  padding: 14px 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(250, 250, 250, 0.9) 100%);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  color: #333;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.form-input {
  width: 100%;
  padding: 14px 18px;
  border: 2px solid rgba(255, 200, 150, 0.5);
  border-radius: 12px;
  font-size: 14px;
  outline: none;
  transition: all 0.3s ease;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(255, 154, 108, 0.08);
  color: #333;
  position: relative;
  z-index: 10;
  pointer-events: auto !important;
  user-select: text !important;
  -webkit-user-select: text !important;
  -moz-user-select: text !important;
  -ms-user-select: text !important;
  cursor: text !important;
  display: block !important;
}

.form-input::placeholder {
  color: #999;
}

.form-input:focus {
  border-color: #ff9a6c;
  box-shadow: 0 0 0 4px rgba(255, 154, 108, 0.15), 0 4px 20px rgba(255, 154, 108, 0.2);
}

.form-hint {
  font-size: 12px;
  color: #996633;
  margin-top: 6px;
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  margin: 30px 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 18px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(255, 250, 240, 0.95) 100%);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  border: 1px solid rgba(255, 200, 150, 0.3);
  box-shadow: 0 8px 32px rgba(255, 154, 108, 0.15);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 28px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1));
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
  font-weight: 500;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  background: linear-gradient(135deg, #ff9a6c 0%, #ff6b35 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.record-details {
  margin: 30px 0;
}

.record-details h4 {
  font-size: 16px;
  color: #333;
  margin-bottom: 15px;
  font-weight: 600;
}

.record-items {
  display: flex;
  gap: 12px;
}

.record-item {
  flex: 1;
  padding: 18px;
  border-radius: 14px;
  text-align: center;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.record-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12);
}

.record-item.win {
  background: linear-gradient(135deg, rgba(255, 245, 230, 0.98) 0%, rgba(255, 235, 210, 0.95) 100%);
  border: 1px solid rgba(255, 220, 180, 0.6);
}

.record-item.loss {
  background: linear-gradient(135deg, rgba(255, 248, 235, 0.98) 0%, rgba(255, 240, 215, 0.95) 100%);
  border: 1px solid rgba(255, 225, 190, 0.6);
}

.record-item.draw {
  background: linear-gradient(135deg, rgba(255, 250, 240, 0.98) 0%, rgba(255, 242, 220, 0.95) 100%);
  border: 1px solid rgba(255, 230, 200, 0.6);
}

.record-label {
  display: block;
  font-size: 12px;
  color: #a67c52;
  margin-bottom: 6px;
  font-weight: 500;
}

.record-value {
  display: block;
  font-size: 22px;
  font-weight: bold;
  color: #c4874b;
}

.record-item.win .record-value {
  color: #d4875c;
}

.record-item.loss .record-value {
  color: #d4875c;
}

.record-item.draw .record-value {
  color: #d4875c;
}

.action-buttons {
  display: flex;
  justify-content: center;
  margin-top: 40px;
  margin-bottom: 20px;
}

.save-button {
  min-width: 140px;
}

.back-button-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 10px;
}

@media (max-width: 768px) {
  .stats-section {
    grid-template-columns: 1fr;
  }

  .record-items {
    flex-direction: column;
  }

  .save-button {
    width: 100%;
    max-width: 280px;
  }
}
</style>
