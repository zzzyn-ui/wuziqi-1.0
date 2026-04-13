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

    <!-- 聊天对话框 -->
    <div v-if="showChatDialog" class="chat-dialog-overlay" @click.self="closeChatDialog">
      <div class="chat-dialog">
        <div class="chat-header">
          <div class="chat-header-info">
            <div class="chat-avatar">{{ (chatFriend?.nickname || chatFriend?.username)?.charAt(0) || '?' }}</div>
            <div class="chat-header-text">
              <h3>{{ chatFriend?.nickname || chatFriend?.username }}</h3>
              <p v-if="chatFriend?.online" class="status-online">在线</p>
              <p v-else class="status-offline">离线</p>
            </div>
          </div>
          <button class="close-btn" @click="closeChatDialog">×</button>
        </div>
        <div class="chat-messages" ref="chatMessagesRef">
          <div v-if="isFriendTyping" class="typing-indicator">
            <span>{{ chatFriend?.nickname || chatFriend?.username }} 正在输入...</span>
          </div>
          <div v-if="chatMessages.length === 0" class="chat-empty">
            <p>暂无消息，开始聊天吧</p>
          </div>
          <div
            v-for="message in chatMessages"
            :key="message.id || message.createdAt"
            class="chat-message"
            :class="{ 'message-sent': message.senderId === userStore.userInfo?.id, 'message-received': message.senderId !== userStore.userInfo?.id }"
          >
            <div class="message-bubble">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-time">{{ formatMessageTime(message.createdAt) }}</div>
            </div>
          </div>
        </div>
        <div class="chat-input-area">
          <input
            v-model="chatInput"
            type="text"
            placeholder="输入消息..."
            class="chat-input"
            @input="handleInputChange"
            @keyup.enter="sendChatMessage"
            :disabled="!chatFriend?.online"
          />
          <ActionButton
            variant="primary"
            size="small"
            @click="sendChatMessage"
            :disabled="!chatInput.trim() || !chatFriend?.online"
          >
            发送
          </ActionButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { useGameStore } from '@/store/modules/game'
import { friendApi, userApi } from '@/api'
import { wsClient } from '@/api/websocket'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, TabPane, LoadingState, EmptyState, ListItem, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('friends')

const router = useRouter()
const userStore = useUserStore()
const gameStore = useGameStore()

interface Friend {
  id: number          // 好友关系ID
  userId: number      // 好友的用户ID
  username: string
  nickname?: string
  avatar?: string
  level: number
  rating: number
  online: boolean
  inGame: boolean
  remark?: string     // 好友备注
  groupId?: number    // 分组ID
  createdAt?: string
}

interface FriendRequest {
  id: number
  userId: number      // 请求者的用户ID
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

// 聊天相关状态
const showChatDialog = ref(false)
const chatFriend = ref<Friend | null>(null)
const chatMessages = ref<any[]>([])
const chatInput = ref('')
const chatMessagesRef = ref<HTMLElement | null>(null)
const isFriendTyping = ref(false)
const typingTimer = ref<number | null>(null)
let privateMessageSubscription = ''
let chatHistorySubscription = ''
let typingStatusSubscription = ''

const tabs = computed(() => [
  { label: '我的好友', value: 'friends' },
  { label: '好友申请', value: 'requests', badge: pendingRequests.value.length + gameStore.pendingInvitations.length },
  { label: '查找玩家', value: 'search' }
])

const filteredFriends = computed(() => {
  // 合并 API 返回的好友列表和 store 中的实时状态
  const friendsWithStatus = friends.value.map(friend => {
    const realtimeStatus = gameStore.getFriendStatus(friend.id)
    return {
      ...friend,
      online: realtimeStatus?.online ?? friend.online,
      inGame: realtimeStatus?.inGame ?? friend.inGame,
      status: realtimeStatus?.status ?? (friend.online ? 1 : 0)
    }
  })

  if (!searchText.value) return friendsWithStatus
  const search = searchText.value.toLowerCase()
  return friendsWithStatus.filter(f =>
    f.username.toLowerCase().includes(search) ||
    (f.nickname && f.nickname.toLowerCase().includes(search))
  )
})

const fetchFriends = async () => {
  loading.value = true
  try {
    const response = await friendApi.getFriendList(userStore.userInfo!.id)
    // axios 拦截器返回格式: { code: 200, success: true, data: { list: [...], total: count }, message: "..." }
    if (response.code === 200) {
      friends.value = response.data?.list || []
      console.log('[FriendsView] 好友列表加载成功:', friends.value)
      console.log('[FriendsView] 好友数量:', friends.value.length)
      friends.value.forEach((friend, index) => {
        console.log(`[FriendsView] 好友 ${index}:`, friend)
      })
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
    // axios 拦截器返回格式: { code: 200, success: true, data: { list: [...], total: count }, message: "..." }
    if (response.code === 200) {
      pendingRequests.value = response.data?.list || []
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

const handleChat = async (friend: Friend) => {
  if (!friend.online) {
    ElMessage.warning('好友离线，无法聊天')
    return
  }

  console.log('[FriendsView] 开始与好友聊天:', friend)

  // 确保 WebSocket 已连接
  if (!wsClient.isConnected()) {
    console.log('[FriendsView] WebSocket 未连接，尝试连接...')
    try {
      const token = localStorage.getItem('token')
      if (token) {
        await wsClient.connect(token)
        console.log('[FriendsView] WebSocket 连接成功')
      } else {
        ElMessage.error('请先登录')
        return
      }
    } catch (error) {
      console.error('[FriendsView] WebSocket 连接失败:', error)
      ElMessage.error('连接失败，请稍后重试')
      return
    }
  }

  // 清理旧的订阅
  clearChatSubscriptions()

  chatFriend.value = friend
  chatMessages.value = []
  showChatDialog.value = true

  // 先订阅私聊消息（在请求历史之前，确保不会错过新消息）
  privateMessageSubscription = wsClient.subscribePrivateMessage(userStore.userInfo!.id, (data) => {
    console.log('[FriendsView] 💬 收到私聊消息:', data)
    if (data.type === 'PRIVATE_MESSAGE') {
      // 检查消息是否来自当前聊天的好友
      if (data.senderId === friend.userId || data.receiverId === friend.userId) {
        // 如果是自己发送的消息，检查是否有临时消息需要替换
        if (data.senderId === userStore.userInfo!.id) {
          const tempIndex = chatMessages.value.findIndex(m => m.isTemp === true)
          if (tempIndex !== -1) {
            // 替换临时消息
            chatMessages.value[tempIndex] = {
              id: data.id,
              senderId: data.senderId,
              receiverId: data.receiverId,
              content: data.content,
              createdAt: data.createdAt || new Date().toISOString()
            }
            console.log('[FriendsView] 💬 替换临时消息')
            return
          }

          // 检查是否已存在（避免重复）
          const exists = chatMessages.value.some(m => m.id === data.id)
          if (exists) {
            console.log('[FriendsView] 💬 消息已存在，跳过')
            return
          }
        }

        const message = {
          id: data.id,
          senderId: data.senderId,
          receiverId: data.receiverId,
          content: data.content,
          createdAt: data.createdAt || new Date().toISOString()
        }
        chatMessages.value.push(message)
        console.log('[FriendsView] 💬 添加消息到聊天:', message)
        // 滚动到底部
        nextTick(() => {
          scrollToBottom()
        })
      }
    }
  })

  // 订阅输入状态
  typingStatusSubscription = wsClient.subscribeTypingStatus((data) => {
    console.log('[FriendsView] ⌨️ 收到输入状态:', data)
    if (data.type === 'TYPING_STATUS' && data.friendId === friend.userId) {
      isFriendTyping.value = data.typing
      // 清除之前的定时器
      if (typingTimer.value) {
        clearTimeout(typingTimer.value)
      }
      // 3秒后自动取消输入状态
      if (data.typing) {
        typingTimer.value = window.setTimeout(() => {
          isFriendTyping.value = false
        }, 3000)
      }
    }
  })

  // 请求聊天历史
  console.log('[FriendsView] 📜 请求聊天历史: friendId=', friend.userId)
  wsClient.getChatHistory(friend.userId, 50)

  // 订阅聊天历史响应
  chatHistorySubscription = wsClient.subscribeChatHistory((data) => {
    console.log('[FriendsView] 📜 收到聊天历史响应:', data)
    if (data.type === 'CHAT_HISTORY') {
      if (data.friendId === friend.userId) {
        // 反转消息顺序（从旧到新）
        const historyMessages = (data.messages || []).map((msg: any) => ({
          id: msg.id,
          senderId: msg.senderId,
          receiverId: msg.receiverId,
          content: msg.content,
          createdAt: msg.createdAt || new Date().toISOString()
        }))
        chatMessages.value = historyMessages.reverse()
        console.log('[FriendsView] 📜 聊天历史加载完成，共', chatMessages.value.length, '条消息')
        nextTick(() => {
          scrollToBottom()
        })
      }
    }
  })

  // 标记消息为已读
  wsClient.markMessagesAsRead(friend.userId)
}

const clearChatSubscriptions = () => {
  if (privateMessageSubscription) {
    wsClient.unsubscribe(privateMessageSubscription)
    privateMessageSubscription = ''
  }
  if (chatHistorySubscription) {
    wsClient.unsubscribe(chatHistorySubscription)
    chatHistorySubscription = ''
  }
  if (typingStatusSubscription) {
    wsClient.unsubscribe(typingStatusSubscription)
    typingStatusSubscription = ''
  }
}

const closeChatDialog = () => {
  showChatDialog.value = false
  chatFriend.value = null
  chatMessages.value = []
  chatInput.value = ''
  isFriendTyping.value = false
  if (typingTimer.value) {
    clearTimeout(typingTimer.value)
    typingTimer.value = null
  }
  clearChatSubscriptions()
}

const sendChatMessage = () => {
  if (!chatFriend.value || !chatInput.value.trim()) {
    return
  }

  if (!chatFriend.value.online) {
    ElMessage.warning('好友离线，无法发送消息')
    return
  }

  const content = chatInput.value.trim()
  const tempId = Date.now() // 临时ID，用于防止重复

  console.log('[FriendsView] 📤 发送消息:', { friendId: chatFriend.value.userId, content })

  // 立即添加到聊天列表（乐观UI）
  const tempMessage = {
    id: tempId,
    senderId: userStore.userInfo!.id,
    receiverId: chatFriend.value.userId,
    content: content,
    createdAt: new Date().toISOString(),
    isTemp: true // 标记为临时消息
  }
  chatMessages.value.push(tempMessage)
  chatInput.value = ''

  nextTick(() => {
    scrollToBottom()
  })

  // 发送停止输入状态
  wsClient.sendTypingStatus(chatFriend.value.userId, false)

  try {
    wsClient.sendPrivateMessage(chatFriend.value.userId, content)
  } catch (error) {
    console.error('[FriendsView] 发送消息失败:', error)
    ElMessage.error('发送失败，请重试')
    // 移除临时消息
    const index = chatMessages.value.findIndex(m => m.id === tempId)
    if (index !== -1) {
      chatMessages.value.splice(index, 1)
    }
  }
}

// 监听输入状态
const handleInputChange = () => {
  if (!chatFriend.value) return

  // 发送正在输入状态
  wsClient.sendTypingStatus(chatFriend.value.userId, true)

  // 清除之前的定时器
  if (typingTimer.value) {
    clearTimeout(typingTimer.value)
  }

  // 3秒后自动发送停止输入状态
  typingTimer.value = window.setTimeout(() => {
    wsClient.sendTypingStatus(chatFriend.value!.userId, false)
  }, 3000)
}

const scrollToBottom = () => {
  if (chatMessagesRef.value) {
    chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  }
}

const formatMessageTime = (time: string | Date) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) { // 1分钟内
    return '刚刚'
  } else if (diff < 3600000) { // 1小时内
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) { // 24小时内
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
           date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
}

const handleInvite = (friend: Friend) => {
  // 使用 friend.userId（好友的用户ID）发送游戏邀请
  gameStore.sendGameInvitation(friend.userId, 'casual')
}

const handleDelete = async (friend: Friend) => {
  try {
    await ElMessageBox.confirm(
      `确定删除好友 ${friend.nickname || friend.username}？`,
      '确认删除',
      { type: 'warning' }
    )
    // 使用 friend.userId（好友的用户ID）而不是 friend.id（好友关系ID）
    await friendApi.deleteFriend(userStore.userInfo!.id, friend.userId)
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
    // axios 拦截器返回格式: { code: 200, success: true, data: { list: [...], total: count }, message: "..." }
    if (response.code === 200) {
      searchResults.value = response.data?.list || []
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
    // axios 拦截器返回格式: { code: 200, success: true, data: { list: [...], total: count }, message: "..." }
    if (!searchResponse.data?.list || searchResponse.data.list.length === 0) {
      ElMessage.error('未找到该用户')
      return
    }

    const targetUser = searchResponse.data.list[0]
    await friendApi.sendFriendRequest({
      userId: userStore.userInfo!.id,
      targetUserId: targetUser.id,
      message: addForm.value.message
    })

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

// 组件挂载时初始化
onMounted(() => {
  applyPageTheme(theme)
  fetchFriends()
  fetchRequests()
  // 初始化好友系统实时通知
  gameStore.initFriendSystem()
})

// 组件卸载时清理
onUnmounted(() => {
  gameStore.cleanupFriendSystem()
  clearChatSubscriptions()
  if (typingTimer.value) {
    clearTimeout(typingTimer.value)
  }
})
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
  position: relative;
  z-index: 10;
  pointer-events: auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.friend-actions :deep(.action-btn),
.request-actions :deep(.action-btn) {
  pointer-events: auto !important;
  cursor: pointer !important;
  position: relative;
  z-index: 10;
  opacity: 1 !important;
  visibility: visible !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.friend-actions :deep(button),
.request-actions :deep(button) {
  pointer-events: auto !important;
  cursor: pointer !important;
  position: relative;
  z-index: 10;
  opacity: 1 !important;
  visibility: visible !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  min-width: 60px;
  padding: 6px 12px;
  font-size: 13px;
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

/* 聊天对话框样式 */
.chat-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
  padding: 20px;
}

.chat-dialog {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 500px;
  height: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  animation: slideUp 0.3s ease-out;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.chat-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-avatar {
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

.chat-header-text h3 {
  font-size: 16px;
  color: #333;
  margin: 0 0 4px 0;
}

.chat-header-text p {
  font-size: 12px;
  margin: 0;
}

.status-online {
  color: #52c41a;
}

.status-offline {
  color: #999;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.typing-indicator {
  padding: 10px 14px;
  background: #f0f0f0;
  border-radius: 12px;
  margin-bottom: 8px;
  align-self: flex-start;
  font-size: 13px;
  color: #666;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.chat-message {
  display: flex;
}

.message-sent {
  justify-content: flex-end;
}

.message-received {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  position: relative;
}

.message-sent .message-bubble {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.message-received .message-bubble {
  background: #f5f5f5;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-content {
  word-wrap: break-word;
  white-space: pre-wrap;
}

.message-time {
  font-size: 11px;
  margin-top: 4px;
  opacity: 0.7;
}

.message-sent .message-time {
  text-align: right;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.3s;
}

.chat-input:focus {
  border-color: #667eea;
}

.chat-input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .chat-dialog {
    height: 100vh;
    max-height: 100vh;
    border-radius: 0;
  }
}
</style>
