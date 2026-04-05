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
                <label class="form-label">用户名 / 昵称</label>
                <input
                  v-model="profileForm.nickname"
                  type="text"
                  placeholder="请输入用户名/昵称"
                  class="form-input"
                  maxlength="20"
                />
                <div class="form-hint">用户名和昵称是同一个，修改后全局生效</div>
              </div>

              <div class="form-group">
                <label class="form-label">邮箱</label>
                <div class="form-value">{{ userStore.userInfo?.email || '未设置' }}</div>
              </div>

              <!-- 统计信息 -->
              <div class="stats-section">
                <div class="stat-card">
                  <div class="stat-icon">⭐</div>
                  <div class="stat-info">
                    <div class="stat-label">等级</div>
                    <div class="stat-value">Lv.{{ userStats.level || userStore.userInfo?.level || 1 }}</div>
                  </div>
                </div>

                <div class="stat-card">
                  <div class="stat-icon">🏆</div>
                  <div class="stat-info">
                    <div class="stat-label">积分</div>
                    <div class="stat-value">{{ userStats.rating || userStore.userInfo?.rating || 1200 }}</div>
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
                >
                  保存修改
                </ActionButton>
                <ActionButton
                  variant="ghost"
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { userApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, LoadingState, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('profile')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchUserStats()
})

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)

const profileForm = reactive({
  nickname: userStore.userInfo?.nickname || ''
})

const userStats = ref({
  totalGames: 0,
  wins: 0,
  losses: 0,
  draws: 0,
  winRate: 0,
  level: userStore.userInfo?.level || 1,
  rating: userStore.userInfo?.rating || 1200
})

const fetchUserStats = async () => {
  if (!userStore.userInfo?.id) return

  loading.value = true
  try {
    const response = await userApi.getUserStats(userStore.userInfo.id)
    if (response.data && response.data.code === 200) {
      userStats.value = {
        ...userStats.value,
        ...(response.data.data || {})
      }
    }
  } catch (error: any) {
    console.error('[ProfileView] 获取用户统计失败:', error)
    // 使用默认值
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!userStore.userInfo?.id) return

  saving.value = true
  try {
    await userApi.updateUserInfo(userStore.userInfo.id, {
      nickname: profileForm.nickname,
      username: profileForm.nickname
    })

    // 更新本地store - 同时更新nickname和username
    userStore.userInfo!.nickname = profileForm.nickname
    userStore.userInfo!.username = profileForm.nickname

    ElMessage.success('保存成功')
  } catch (error: any) {
    console.error('[ProfileView] 保存失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 700px;
  margin: 0 auto;
  padding: 0 20px;
}

.loading-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-content {
  padding: 20px 0;
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
  background: linear-gradient(135deg, #ff9a8b 0%, #ffc3a0 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: bold;
  margin-bottom: 15px;
  box-shadow: 0 4px 15px rgba(255, 154, 139, 0.3);
}

.form-section {
  max-width: 500px;
  margin: 0 auto;
}

.form-group {
  margin-bottom: 25px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  margin-bottom: 8px;
}

.form-value {
  padding: 12px 16px;
  background: #f5f5f5;
  border-radius: 8px;
  color: #333;
  font-size: 14px;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #ff9a8b;
}

.form-hint {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
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
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.stat-icon {
  font-size: 24px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 3px;
}

.stat-value {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.record-details {
  margin: 30px 0;
}

.record-details h4 {
  font-size: 16px;
  color: #333;
  margin-bottom: 15px;
}

.record-items {
  display: flex;
  gap: 10px;
}

.record-item {
  flex: 1;
  padding: 15px;
  border-radius: 10px;
  text-align: center;
}

.record-item.win {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.record-item.loss {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
}

.record-item.draw {
  background: linear-gradient(135deg, #f5f5f5 0%, #e5e5e5 100%);
}

.record-label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 5px;
}

.record-value {
  display: block;
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.record-item.win .record-value {
  color: #67c23a;
}

.record-item.loss .record-value {
  color: #f56c6c;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-top: 30px;
}

@media (max-width: 768px) {
  .stats-section {
    grid-template-columns: 1fr;
  }

  .record-items {
    flex-direction: column;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
