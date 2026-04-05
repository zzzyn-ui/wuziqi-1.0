<template>
  <div class="rank-view-unified">
    <PageHeader
      title="🏆 排行榜"
      subtitle="查看玩家积分排名"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <ContentCard>
        <template #header>
          <div class="card-title-section">
            <h3>排行榜</h3>
          </div>
        </template>

        <TabPane
          v-model="activeTab"
          :tabs="tabs"
        >
          <template #default>
            <div v-if="loading" class="loading-wrapper">
              <LoadingState text="加载中..." />
            </div>

            <div v-else-if="rankList.length === 0" class="empty-wrapper">
              <EmptyState
                icon="🏆"
                title="暂无数据"
                description="暂时没有排行榜数据"
              />
            </div>

            <div v-else class="rank-list">
              <ListItem
                v-for="(player, index) in rankList"
                :key="player.id"
                :title="player.nickname || player.username"
                :subtitle="`Lv.${player.level} | ${player.totalGames}场`"
                :active="player.isMe"
              >
                <template #avatar>
                  <div class="rank-avatar" :class="getRankClass(index)">
                    <span v-if="index < 3" class="medal-icon">
                      {{ index === 0 ? '🥇' : index === 1 ? '🥈' : '🥉' }}
                    </span>
                    <span v-else class="rank-number">#{{ player.rank }}</span>
                  </div>
                </template>

                <template #title-suffix>
                  <span v-if="player.isMe" class="tag tag-warning">我</span>
                  <span v-if="player.online" class="tag tag-success">在线</span>
                  <span v-else class="tag tag-info">离线</span>
                </template>

                <template #extra>
                  <div class="player-stats">
                    <div class="stat-item">
                      <span class="stat-label">积分</span>
                      <span class="stat-value">{{ player.rating }}</span>
                    </div>
                    <div class="stat-item">
                      <span class="stat-label">胜率</span>
                      <span class="stat-value">{{ player.winRate }}%</span>
                    </div>
                  </div>
                </template>
              </ListItem>
            </div>
          </template>
        </TabPane>

        <template #footer>
          <div v-if="myRank > 0" class="my-rank-footer">
            <span class="my-rank-label">我的排名</span>
            <span class="my-rank-value">#{{ myRank }}</span>
          </div>
        </template>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { rankApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, TabPane, LoadingState, EmptyState, ListItem } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('rank')

// 应用主题到CSS变量
onMounted(() => {
  applyPageTheme(theme)
})

const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('all')
const rankList = ref<any[]>([])
const myRank = ref(0)

const tabs = [
  { label: '总榜', value: 'all' },
  { label: '日榜', value: 'daily' },
  { label: '周榜', value: 'weekly' },
  { label: '月榜', value: 'monthly' }
]

// 获取排名样式
const getRankClass = (index: number): string => {
  if (index === 0) return 'rank-first'
  if (index === 1) return 'rank-second'
  if (index === 2) return 'rank-third'
  return 'rank-normal'
}

// 获取排行榜数据
const fetchRankList = async () => {
  loading.value = true
  try {
    const response = await rankApi.getRankList(activeTab.value)

    if (response.data && response.data.code === 200) {
      const list = response.data.data.list || []
      rankList.value = list.map((player: any) => ({
        ...player,
        isMe: player.id === userStore.userInfo?.id
      }))
      myRank.value = response.data.data.myRank || 0
    } else {
      throw new Error(response.data?.message || '获取失败')
    }
  } catch (error: any) {
    console.error('[RankView] 获取排行榜失败:', error)
    ElMessage.error('获取排行榜失败')

    // 失败时显示当前用户
    if (userStore.userInfo) {
      rankList.value = [{
        ...userStore.userInfo,
        isMe: true,
        totalGames: 0,
        winRate: 0,
        rank: 1
      }]
    }
  } finally {
    loading.value = false
  }
}

// 自动刷新排行榜（每30秒）以更新在线状态
let refreshInterval: number | null = null

const startAutoRefresh = () => {
  if (refreshInterval) return

  refreshInterval = window.setInterval(() => {
    if (!loading.value) {
      console.log('[RankView] 自动刷新排行榜')
      fetchRankList()
    }
  }, 30000)
}

const stopAutoRefresh = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

// 监听标签切换
watch(activeTab, () => {
  fetchRankList()
})

onMounted(() => {
  fetchRankList()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.rank-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.card-title-section h3 {
  font-size: 20px;
  color: #333;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.rank-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.rank-avatar.rank-first {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  font-size: 24px;
}

.rank-avatar.rank-second {
  background: linear-gradient(135deg, #c0c0c0 0%, #e8e8e8 100%);
  color: #666;
}

.rank-avatar.rank-third {
  background: linear-gradient(135deg, #cd7f32 0%, #e6a173 100%);
}

.rank-avatar.rank-normal {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  color: #996600;
  font-size: 16px;
}

.medal-icon {
  font-size: 28px;
}

.player-stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #999;
  display: block;
  margin-bottom: 3px;
}

.stat-value {
  font-size: 18px;
  font-weight: bold;
  color: #ffd700;
}

.my-rank-footer {
  background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);
  color: #996600;
  padding: 15px 20px;
  border-radius: 0 0 16px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
}

.my-rank-label {
  font-size: 14px;
  font-weight: 500;
}

.my-rank-value {
  font-size: 20px;
  font-weight: bold;
}

@media (max-width: 768px) {
  .player-stats {
    flex-wrap: wrap;
    gap: 10px;
  }

  .stat-item {
    flex: 0 0 auto;
  }
}
</style>
