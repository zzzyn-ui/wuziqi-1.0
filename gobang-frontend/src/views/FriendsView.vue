<template>
  <div class="friends-view-unified">
    <PageHeader
      title="👥 好友系统"
      subtitle="添加好友，一起下棋"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    >
      <template #actions>
        <ActionButton variant="primary" @click="showAddDialog = true">
          <template #icon>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </template>
          添加好友
        </ActionButton>
      </template>
    </PageHeader>

    <div class="page-content-wrapper">
      <ContentCard>
        <template #header>
          <TabPane v-model="activeTab" :tabs="tabs" />
        </template>

        <template #default>
          <!-- 好友列表 -->
          <div v-if="activeTab === 'friends'" class="tab-content">
            <div class="search-bar">
              <input
                v-model="searchText"
                type="text"
                placeholder="搜索好友..."
                class="search-input"
              />
            </div>

            <div v-if="loading" class="loading-wrapper">
              <LoadingState text="加载中..." />
            </div>

            <div v-else-if="filteredFriends.length === 0" class="empty-wrapper">
              <EmptyState
                icon="👥"
                title="暂无好友"
                description="通过搜索添加好友，一起下棋吧"
              />
            </div>

            <div v-else class="friend-list">
              <ListItem
                v-for="friend in filteredFriends"
                :key="friend.id"
                :title="friend.nickname || friend.username"
                :subtitle="`Lv.${friend.level} | ${friend.rating}分`"
                clickable
              >
                <template #avatar>
                  <div class="friend-avatar">
                    {{ (friend.nickname || friend.username)?.charAt(0) || '?' }}
                  </div>
                </template>

                <template #title-suffix>
                  <span v-if="friend.online" class="tag tag-success">在线</span>
                  <span v-else class="tag tag-info">离线</span>
                </template>

                <template #extra>
                  <div class="friend-actions">
                    <ActionButton
                      variant="ghost"
                      size="small"
                      :disabled="!friend.online"
                      @click="handleChat(friend)"
                    >
                      聊天
                    </ActionButton>
                    <ActionButton
                      variant="ghost"
                      size="small"
                      :disabled="!friend.online || friend.inGame"
                      @click="handleInvite(friend)"
                    >
                      邀请
                    </ActionButton>
                    <ActionButton
                      variant="danger"
                      size="small"
                      @click="handleDelete(friend)"
                    >
                      删除
                    </ActionButton>
                  </div>
                </template>
              </ListItem>
            </div>
          </div>

          <!-- 好友申请 -->
          <div v-else-if="activeTab === 'requests'" class="tab-content">
            <div v-if="loading" class="loading-wrapper">
              <LoadingState text="加载中..." />
            </div>

            <div v-else-if="pendingRequests.length === 0" class="empty-wrapper">
              <EmptyState
                icon="📬"
                title="暂无好友申请"
                description="等待别人添加你为好友"
              />
            </div>

            <div v-else class="request-list">
              <ListItem
                v-for="request in pendingRequests"
                :key="request.id"
                :title="request.nickname || request.username"
                :subtitle="request.message || '请求添加你为好友'"
                clickable
              >
                <template #avatar>
                  <div class="request-avatar">
                    {{ (request.nickname || request.username)?.charAt(0) || '?' }}
                  </div>
                </template>

                <template #extra>
                  <div class="request-actions">
                    <ActionButton
                      variant="success"
                      size="small"
                      @click="handleRequest(request, true)"
                    >
                      同意
                    </ActionButton>
                    <ActionButton
                      variant="ghost"
                      size="small"
                      @click="handleRequest(request, false)"
                    >
                      拒绝
                    </ActionButton>
                  </div>
                </template>
              </ListItem>
            </div>
          </div>

          <!-- 查找玩家 -->
          <div v-else-if="activeTab === 'search'" class="tab-content">
            <div class="search-section">
              <div class="search-input-group">
                <input
                  v-model="playerSearch"
                  type="text"
                  placeholder="输入玩家用户名搜索"
                  class="search-input-large"
                  @keyup.enter="searchPlayer"
                />
                <ActionButton variant="primary" @click="searchPlayer">
                  <template #icon>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="11" cy="11" r="8"></circle>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                  </template>
                  搜索
                </ActionButton>
              </div>
            </div>

            <div v-if="searchResults.length > 0" class="search-results">
              <ListItem
                v-for="player in searchResults"
                :key="player.id"
                :title="player.nickname || player.username"
                :subtitle="`Lv.${player.level} | ${player.rating}分`"
                clickable
              >
                <template #avatar>
                  <div class="player-avatar">
                    {{ (player.nickname || player.username)?.charAt(0) || '?' }}
                  </div>
                </template>

                <template #title-suffix>
                  <span v-if="player.online" class="tag tag-success">在线</span>
                  <span v-else class="tag tag-info">离线</span>
                </template>

                <template #extra>
                  <ActionButton variant="primary" size="small" @click="sendRequest(player)">
                    添加
                  </ActionButton>
                </template>
              </ListItem>
            </div>
          </div>
        </template>
      </ContentCard>
    </div>

    <!-- 添加好友对话框 -->
    <div v-if="showAddDialog" class="dialog-overlay" @click.self="showAddDialog = false">
      <div class="dialog">
        <div class="dialog-header">
          <h3>添加好友</h3>
          <button class="close-btn" @click="showAddDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>用户名</label>
            <input
              v-model="addForm.username"
              type="text"
              placeholder="请输入对方用户名"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label>验证消息</label>
            <textarea
              v-model="addForm.message"
              placeholder="我是..."
              class="form-textarea"
              maxlength="100"
              rows="3"
            ></textarea>
            <div class="char-count">{{ addForm.message.length }}/100</div>
          </div>
        </div>
        <div class="dialog-footer">
          <ActionButton variant="ghost" @click="showAddDialog = false">取消</ActionButton>
          <ActionButton variant="primary" :loading="adding" @click="addFriend">
            发送申请
          </ActionButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { friendApi, userApi } from '@/api'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, TabPane, LoadingState, EmptyState, ListItem, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('friends')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchFriends()
  fetchRequests()
})

const userStore = useUserStore()

interface Friend {
  id: number
  username: string
  nickname?: string
  avatar?: string
  level: number
  rating: number
  online: boolean
  inGame: boolean
}

interface FriendRequest {
  id: number
  username: string
  nickname?: string
  avatar?: string
  message?: string
  createdAt: Date
}

const activeTab = ref('friends')
const loading = ref(false)
const searchText = ref('')
const showAddDialog = ref(false)
const adding = ref(false)
const playerSearch = ref('')

const friends = ref<Friend[]>([])
const pendingRequests = ref<FriendRequest[]>([])
const searchResults = ref<Friend[]>([])

const addForm = ref({
  username: '',
  message: ''
})

const tabs = [
  { label: '我的好友', value: 'friends' },
  { label: '好友申请', value: 'requests', badge: pendingRequests.value.length },
  { label: '查找玩家', value: 'search' }
]

const filteredFriends = computed(() => {
  if (!searchText.value) return friends.value
  const search = searchText.value.toLowerCase()
  return friends.value.filter(f =>
    f.username.toLowerCase().includes(search) ||
    (f.nickname && f.nickname.toLowerCase().includes(search))
  )
})

const fetchFriends = async () => {
  loading.value = true
  try {
    const response = await friendApi.getFriendList(userStore.userInfo!.id)
    if (response.data && response.data.code === 200) {
      friends.value = response.data.data || []
    }
  } catch (error: any) {
    console.error('[FriendsView] 获取好友列表失败:', error)
    ElMessage.error('获取好友列表失败')
  } finally {
    loading.value = false
  }
}

const fetchRequests = async () => {
  loading.value = true
  try {
    const response = await friendApi.getFriendRequests(userStore.userInfo!.id)
    if (response.data && response.data.code === 200) {
      pendingRequests.value = response.data.data || []
      // 更新徽章数量
      tabs[1].badge = pendingRequests.value.length
    }
  } catch (error: any) {
    console.error('[FriendsView] 获取好友申请失败:', error)
    ElMessage.error('获取好友申请失败')
  } finally {
    loading.value = false
  }
}

const handleChat = (friend: Friend) => {
  ElMessage.success('打开聊天窗口')
}

const handleInvite = (friend: Friend) => {
  ElMessage.success(`邀请 ${friend.nickname || friend.username} 对战`)
}

const handleDelete = async (friend: Friend) => {
  try {
    await ElMessageBox.confirm(
      `确定删除好友 ${friend.nickname || friend.username}？`,
      '确认删除',
      { type: 'warning' }
    )
    await friendApi.deleteFriend(userStore.userInfo!.id, friend.id)
    ElMessage.success('已删除好友')
    fetchFriends()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('[FriendsView] 删除好友失败:', error)
      ElMessage.error('删除好友失败')
    }
  }
}

const handleRequest = async (request: FriendRequest, accept: boolean) => {
  try {
    await friendApi.handleFriendRequest(request.id, userStore.userInfo!.id, accept)
    ElMessage.success(accept ? '已添加好友' : '已拒绝申请')
    pendingRequests.value = pendingRequests.value.filter(r => r.id !== request.id)
    tabs[1].badge = pendingRequests.value.length
    if (accept) {
      fetchFriends()
    }
  } catch (error: any) {
    console.error('[FriendsView] 处理好友申请失败:', error)
    ElMessage.error('操作失败')
  }
}

const searchPlayer = async () => {
  if (!playerSearch.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }

  loading.value = true
  try {
    const response = await userApi.searchUsers(playerSearch.value)
    if (response.data && response.data.code === 200) {
      searchResults.value = response.data.data || []
    }
  } catch (error: any) {
    console.error('[FriendsView] 搜索玩家失败:', error)
    ElMessage.error('搜索失败')
  } finally {
    loading.value = false
  }
}

const addFriend = async () => {
  if (!addForm.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }

  adding.value = true
  try {
    // 先搜索用户获取ID
    const searchResponse = await userApi.searchUsers(addForm.value.username)
    if (!searchResponse.data?.data || searchResponse.data.data.length === 0) {
      ElMessage.error('未找到该用户')
      return
    }

    const targetUser = searchResponse.data.data[0]
    await friendApi.sendFriendRequest({
      userId: userStore.userInfo!.id,
      targetUserId: targetUser.id,
      message: addForm.value.message
    })

    ElMessage.success('好友申请已发送')
    showAddDialog.value = false
    addForm.value = { username: '', message: '' }
  } catch (error: any) {
    console.error('[FriendsView] 发送好友申请失败:', error)
    ElMessage.error('发送申请失败')
  } finally {
    adding.value = false
  }
}

const sendRequest = (player: any) => {
  addForm.value.username = player.username
  showAddDialog.value = true
  searchResults.value = []
}
</script>

<style scoped>
.friends-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.tab-content {
  padding: 20px 0;
}

.search-bar {
  margin-bottom: 20px;
}

.search-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.search-input:focus {
  border-color: #667eea;
}

.loading-wrapper,
.empty-wrapper {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.friend-list,
.request-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.friend-avatar,
.request-avatar,
.player-avatar {
  width: 45px;
  height: 45px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 18px;
}

.friend-actions,
.request-actions {
  display: flex;
  gap: 10px;
}

.search-section {
  margin-bottom: 20px;
}

.search-input-group {
  display: flex;
  gap: 10px;
}

.search-input-large {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.search-input-large:focus {
  border-color: #667eea;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

/* 对话框 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.dialog {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 450px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.dialog-header h3 {
  font-size: 18px;
  color: #333;
  margin: 0;
}

.close-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: #f5f5f5;
  border-radius: 50%;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
}

.close-btn:hover {
  background: #e0e0e0;
}

.dialog-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  border-color: #667eea;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .search-input-group {
    flex-direction: column;
  }

  .friend-actions,
  .request-actions {
    flex-wrap: wrap;
  }

  .dialog {
    margin: 20px;
  }
}
</style>
