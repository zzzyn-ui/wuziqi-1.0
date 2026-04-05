<template>
  <div class="room-view">
    <div class="header">
      <h1>游戏大厅</h1>
      <p class="subtitle">创建房间或加入好友的房间</p>
    </div>

    <div class="content">
      <div class="card create-card">
        <h2>创建房间</h2>
        <el-form :model="createForm" label-width="100px">
          <el-form-item label="房间名称">
            <el-input v-model="createForm.roomName" placeholder="输入房间名称" maxlength="20" show-word-limit />
          </el-form-item>
          <el-form-item label="游戏模式">
            <el-radio-group v-model="createForm.gameMode">
              <el-radio value="CLASSIC">经典模式</el-radio>
              <el-radio value="BLITZ">闪电模式</el-radio>
              <el-radio value="RENJU">五子规则</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="房间密码">
            <el-input v-model="createForm.password" type="password" placeholder="留空为公开房间" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleCreateRoom" :loading="creating" size="large">
              创建房间
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="divider">
        <span>或</span>
      </div>

      <div class="card join-card">
        <h2>加入房间</h2>
        <el-form :model="joinForm" label-width="100px">
          <el-form-item label="房间ID">
            <el-input v-model="joinForm.roomId" placeholder="输入房间ID" />
          </el-form-item>
          <el-form-item label="房间密码">
            <el-input v-model="joinForm.password" type="password" placeholder="如有密码请输入" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="success" @click="handleJoinRoom" :loading="joining" size="large">
              加入房间
            </el-button>
          </el-form-item>
        </el-form>

        <div class="quick-join">
          <el-divider>快速加入</el-divider>
          <div class="room-list">
            <div v-if="publicRooms.length === 0" class="empty-state">
              <el-empty description="暂无公开房间" />
            </div>
            <div v-else class="room-items">
              <div
                v-for="room in publicRooms"
                :key="room.id"
                class="room-item"
                @click="quickJoin(room)"
              >
                <div class="room-info">
                  <div class="room-name">{{ room.name }}</div>
                  <div class="room-meta">
                    <span>{{ room.mode }}</span>
                    <span>{{ room.playerCount }}/2</span>
                  </div>
                </div>
                <el-button type="primary" size="small">加入</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { wsClient } from '@/api/websocket'

const router = useRouter()
const userStore = useUserStore()

const creating = ref(false)
const joining = ref(false)
const publicRooms = ref<any[]>([])

const createForm = reactive({
  roomName: '',
  gameMode: 'CLASSIC',
  password: ''
})

const joinForm = reactive({
  roomId: '',
  password: ''
})

// 获取公开房间列表
const fetchPublicRooms = () => {
  // TODO: 调用API获取公开房间列表
  publicRooms.value = []
}

// 创建房间
const handleCreateRoom = async () => {
  if (!createForm.roomName.trim()) {
    ElMessage.warning('请输入房间名称')
    return
  }

  creating.value = true
  try {
    wsClient.send('/app/room/create', {
      roomName: createForm.roomName,
      gameMode: createForm.gameMode,
      password: createForm.password,
      userId: userStore.userInfo?.id
    })
    ElMessage.success('房间创建成功')
  } catch (error) {
    ElMessage.error('创建房间失败')
  } finally {
    creating.value = false
  }
}

// 加入房间
const handleJoinRoom = async () => {
  if (!joinForm.roomId.trim()) {
    ElMessage.warning('请输入房间ID')
    return
  }

  joining.value = true
  try {
    wsClient.send('/app/room/join', {
      roomId: joinForm.roomId,
      password: joinForm.password,
      userId: userStore.userInfo?.id
    })
    ElMessage.success('正在加入房间...')
  } catch (error) {
    ElMessage.error('加入房间失败')
  } finally {
    joining.value = false
  }
}

// 快速加入
const quickJoin = (room: any) => {
  joinForm.roomId = room.id
  handleJoinRoom()
}

// 订阅房间创建成功消息
let createSubId = ''
const subscribeRoomCreated = () => {
  createSubId = wsClient.subscribe('/user/queue/room/created', (data: any) => {
    router.push(`/game/${data.roomId}`)
  })
}

// 订阅房间加入成功消息
let joinSubId = ''
const subscribeRoomJoined = () => {
  joinSubId = wsClient.subscribe('/user/queue/room/joined', (data: any) => {
    if (data.success) {
      router.push(`/game/${data.roomId}`)
    } else {
      ElMessage.error(data.message || '加入房间失败')
    }
  })
}

// 订阅公开房间列表更新
let roomListSubId = ''
const subscribeRoomList = () => {
  roomListSubId = wsClient.subscribe('/topic/rooms/public', (data: any) => {
    console.log('[Room] 收到房间列表:', data)
    publicRooms.value = data.rooms || []
  })
}

// 请求房间列表
const requestRoomList = () => {
  console.log('[Room] 请求房间列表')
  wsClient.send('/app/room/list', {})
}

onMounted(async () => {
  // 确保WebSocket已连接
  const token = localStorage.getItem('token')
  if (token && !wsClient.isConnected()) {
    try {
      await wsClient.connect(token)
    } catch (error) {
      console.error('[Room] WebSocket连接失败:', error)
    }
  }

  subscribeRoomCreated()
  subscribeRoomJoined()
  subscribeRoomList()
  // 订阅成功后请求房间列表
  requestRoomList()
})

onUnmounted(() => {
  if (createSubId) wsClient.unsubscribe(createSubId)
  if (joinSubId) wsClient.unsubscribe(joinSubId)
  if (roomListSubId) wsClient.unsubscribe(roomListSubId)
})
</script>

<style scoped>
.room-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  padding: 40px 20px;
}

.header {
  text-align: center;
  margin-bottom: 40px;
}

.header h1 {
  font-size: 36px;
  color: #ff6b35;
  margin-bottom: 10px;
}

.subtitle {
  font-size: 16px;
  color: #666;
}

.content {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 30px;
  align-items: start;
}

.card {
  background: white;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(255, 107, 53, 0.1);
}

.card h2 {
  font-size: 24px;
  color: #ff6b35;
  margin-bottom: 20px;
  text-align: center;
}

.divider {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}

.divider span {
  font-size: 24px;
  color: #999;
  padding: 10px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  border-radius: 20px;
}

:deep(.el-form-item__label) {
  color: #666;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #ff8c61 0%, #ff6b35 100%);
  border: none;
  width: 100%;
}

:deep(.el-button--success) {
  background: linear-gradient(135deg, #67c23a 0%, #5daf34 100%);
  border: none;
  width: 100%;
}

:deep(.el-radio) {
  margin-right: 15px;
}

.quick-join {
  margin-top: 20px;
}

.room-list {
  margin-top: 15px;
  max-height: 300px;
  overflow-y: auto;
}

.empty-state {
  padding: 20px 0;
}

.room-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.room-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  background: #fafafa;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.room-item:hover {
  background: #fff5f0;
  transform: translateX(5px);
}

.room-info {
  flex: 1;
}

.room-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
}

.room-meta {
  font-size: 12px;
  color: #999;
}

.room-meta span {
  margin-right: 15px;
}

:deep(.el-divider__text) {
  background: white;
  color: #999;
}

@media (max-width: 768px) {
  .content {
    grid-template-columns: 1fr;
  }

  .divider {
    height: auto;
    padding: 10px 0;
  }

  .card {
    padding: 20px;
  }
}
</style>
