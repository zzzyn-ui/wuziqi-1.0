import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { ElMessage } from 'element-plus'
import type { GameMoveDto } from '@/types/game'

// WebSocket 配置
const WS_CONFIG = {
  // 开发环境使用8080端口，生产环境使用相对路径
  brokerURL: import.meta.env.DEV
    ? `http://${location.hostname}:8080/ws`
    : `${location.protocol}//${location.host}/ws`,
  reconnectDelay: 5000,
  heartbeatIncoming: 4000,
  heartbeatOutgoing: 4000,
  // 启用调试日志
  debug: (str: string) => {
    console.log('[WebSocket STOMP]', str)
  },
}

// WebSocket 消息类型
export type WSMessageType = 'game.start' | 'game.move' | 'game.end' | 'player.join' | 'player.leave' | 'friend.status' | 'friend.invitation'

// WebSocket 消息接口
export interface WSMessage<T = any> {
  type: WSMessageType
  data: T
  timestamp: number
}

// 订阅回调类型
type SubscribeCallback<T = any> = (message: T) => void

class WebSocketClient {
  private client: Client | null = null
  private subscriptions: Map<string, any> = new Map()
  private connected: boolean = false
  private connectPromise: Promise<boolean> | null = null
  private shouldReconnect: boolean = true // 是否应该自动重连
  private currentToken: string | null = null // 存储当前 token 用于重连

  /**
   * 连接 WebSocket
   * @param token 认证 token
   * @returns Promise<boolean>
   */
  connect(token: string): Promise<boolean> {
    // 保存 token 用于重连
    this.currentToken = token

    // 标记应该自动重连
    this.shouldReconnect = true

    // 如果已经在连接中，返回现有的Promise
    if (this.connectPromise) {
      return this.connectPromise
    }

    // 如果已经连接，直接返回成功
    if (this.client && this.connected) {
      console.log('[WebSocket] 已连接，跳过重复连接')
      return Promise.resolve(true)
    }

    console.log('[WebSocket] 开始连接到:', WS_CONFIG.brokerURL)
    console.log('[WebSocket] 使用Token:', token.substring(0, 20) + '...')
    console.log('[WebSocket] 使用SockJS降级方案')

    this.connectPromise = new Promise((resolve, reject) => {
      try {
        // 首先尝试使用SockJS
        const sockJS = new SockJS(WS_CONFIG.brokerURL)
        console.log('[WebSocket] SockJS创建成功:', sockJS)

        this.client = new Client({
          webSocketFactory: () => sockJS,
          brokerURL: WS_CONFIG.brokerURL,
          reconnectDelay: WS_CONFIG.reconnectDelay,
          heartbeatIncoming: WS_CONFIG.heartbeatIncoming,
          heartbeatOutgoing: WS_CONFIG.heartbeatOutgoing,
          connectHeaders: {
            Authorization: `Bearer ${token}`,
          },
          debug: WS_CONFIG.debug,
          onConnect: (frame) => {
            console.log('[WebSocket] ✅ 连接成功', frame)
            this.connected = true
            this.connectPromise = null
            resolve(true)
          },
          onDisconnect: (frame) => {
            console.log('[WebSocket] ❌ 断开连接:', frame)
            this.connected = false
            this.connectPromise = null

            // 如果不是主动断开，则自动重连
            if (this.shouldReconnect && this.currentToken) {
              console.log('[WebSocket] 尝试重连...')
              setTimeout(() => {
                if (this.shouldReconnect) {
                  this.connect(this.currentToken)
                }
              }, WS_CONFIG.reconnectDelay)
            }
          },
          onStompError: (frame) => {
            console.error('[WebSocket] ❌ STOMP错误:', frame)
            console.error('[WebSocket] 错误命令:', frame?.command)
            console.error('[WebSocket] 错误头:', frame?.headers)
            console.error('[WebSocket] 错误体:', frame?.body)

            this.connected = false
            this.connectPromise = null

            // 临时：不拒绝，让连接继续尝试
            // ElMessage.error('WebSocket连接失败: ' + (frame?.headers?.['message'] || '未知错误'))
            // reject(new Error('WebSocket STOMP错误'))
          },
          onWebSocketError: (error) => {
            console.error('[WebSocket] ❌ WebSocket错误:', error)
            this.connected = false
            this.connectPromise = null
            ElMessage.error('WebSocket连接错误')
            reject(error)
          },
          onWebSocketClose: (event) => {
            console.log('[WebSocket] WebSocket关闭:', event)
            this.connected = false
            this.connectPromise = null

            // 如果不是主动断开，则自动重连
            if (this.shouldReconnect && this.currentToken) {
              console.log('[WebSocket] 连接关闭，尝试重连...')
              setTimeout(() => {
                if (this.shouldReconnect) {
                  this.connect(this.currentToken)
                }
              }, WS_CONFIG.reconnectDelay)
            }
          },
        })

        // 激活客户端
        console.log('[WebSocket] 激活STOMP客户端')
        this.client.activate()

        // 设置连接超时（延长到20秒）
        setTimeout(() => {
          if (!this.connected && this.connectPromise) {
            console.error('[WebSocket] 连接超时')
            this.connectPromise = null
            reject(new Error('WebSocket连接超时，请检查后端是否启动'))
          }
        }, 20000)

      } catch (error) {
        console.error('[WebSocket] 创建客户端失败:', error)
        this.connectPromise = null
        reject(error)
      }
    })

    return this.connectPromise
  }

  /**
   * 断开 WebSocket 连接
   */
  disconnect(): void {
    // 标记不应该自动重连
    this.shouldReconnect = false

    if (this.client) {
      this.subscriptions.forEach((subscription) => {
        subscription.unsubscribe()
      })
      this.subscriptions.clear()

      this.client.deactivate()
      this.client = null
      this.connected = false
      console.log('[WebSocket] Disconnected')
    }
  }

  /**
   * 发送消息
   * @param destination 目标地址
   * @param body 消息体
   * @param retries 重试次数
   */
  send<T = any>(destination: string, body: T, retries = 3): void {
    if (!this.client || !this.connected) {
      console.error('[WebSocket] 发送失败 - WebSocket 未连接:', { hasClient: !!this.client, connected: this.connected })
      ElMessage.error('WebSocket 未连接，正在重新连接...')
      // 尝试重新连接
      this.reconnectAndSend(destination, body, retries)
      return
    }

    try {
      const bodyStr = JSON.stringify(body)
      console.log(`[WebSocket] 📤 发送消息: ${destination}`, body)

      // 特别追踪聊天相关消息
      if (destination.includes('chat')) {
        console.log('[WebSocket] 💬 [SEND] 发送聊天消息')
        console.log('[WebSocket] 💬 [SEND] destination:', destination)
        console.log('[WebSocket] 💬 [SEND] body:', bodyStr)
      }

      this.client.publish({
        destination,
        body: bodyStr,
      })

      if (destination.includes('chat')) {
        console.log('[WebSocket] ✅💬 [SEND] 聊天消息已发布')
      }
    } catch (error) {
      console.error('[WebSocket] Send error:', error)
      if (retries > 0) {
        console.log(`[WebSocket] 重试发送消息，剩余次数: ${retries - 1}`)
        setTimeout(() => this.send(destination, body, retries - 1), 1000)
      } else {
        ElMessage.error('发送消息失败')
      }
    }
  }

  /**
   * 重新连接并发送消息
   */
  private reconnectAndSend<T = any>(destination: string, body: T, retries: number): void {
    if (this.currentToken) {
      this.connect(this.currentToken).then(() => {
        this.send(destination, body, retries)
      }).catch(() => {
        ElMessage.error('连接失败，请检查网络')
      })
    }
  }

  /**
   * 订阅消息
   * @param destination 目标地址
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribe<T = any>(destination: string, callback: SubscribeCallback<T>): string {
    if (!this.client || !this.connected) {
      ElMessage.error('WebSocket 未连接')
      return ''
    }

    try {
      const subscription = this.client.subscribe(destination, (message: IMessage) => {
        try {
          console.log('[WebSocket] 📥 收到订阅消息:', destination)

          // 特别追踪聊天消息
          if (destination.includes('chat') || destination.includes('queue')) {
            console.log('[WebSocket] 💬 收到聊天/队列消息:', destination)
            console.log('[WebSocket] 💬 消息体:', message.body)
          }

          const data = JSON.parse(message.body) as T
          console.log('[WebSocket] 解析后的数据:', data)
          callback(data)
        } catch (error) {
          console.error('[WebSocket] Parse message error:', error)
        }
      })

      const subscriptionId = `${destination}_${Date.now()}`
      this.subscriptions.set(subscriptionId, subscription)
      console.log('[WebSocket] 订阅成功:', destination, 'ID:', subscriptionId)
      return subscriptionId
    } catch (error) {
      console.error('[WebSocket] Subscribe error:', error)
      ElMessage.error('订阅失败')
      return ''
    }
  }

  /**
   * 取消订阅
   * @param subscriptionId 订阅 ID
   */
  unsubscribe(subscriptionId: string): void {
    const subscription = this.subscriptions.get(subscriptionId)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(subscriptionId)
    }
  }

  /**
   * 发送游戏落子消息
   * @param move 落子信息
   */
  sendMove(move: GameMoveDto): void {
    this.send('/app/game/move', move)
  }

  /**
   * 订阅游戏状态更新
   * @param roomId 房间 ID
   * @param callback 回调函数
   */
  subscribeGameState(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}`, callback)
  }

  /**
   * 订阅游戏移动
   * @param roomId 房间 ID
   * @param callback 回调函数
   */
  subscribeGameMove(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}`, callback)
  }

  /**
   * 订阅游戏结束
   * @param roomId 房间 ID
   * @param callback 回调函数
   */
  subscribeGameEnd(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}`, callback)
  }

  /**
   * 订阅玩家加入
   * @param roomId 房间 ID
   * @param callback 回调函数
   */
  subscribePlayerJoin(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}/join`, callback)
  }

  /**
   * 订阅玩家离开
   * @param roomId 房间 ID
   * @param callback 回调函数
   */
  subscribePlayerLeave(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}/leave`, callback)
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.connected
  }

  // ==================== 好友系统相关 ====================

  /**
   * 订阅好友状态更新
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeFriendStatus(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/friend/status', callback)
  }

  /**
   * 订阅好友游戏邀请
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeFriendInvitation(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/friend/invitation', callback)
  }

  /**
   * 订阅好友邀请响应
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeFriendInvitationResponse(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/friend/invitation/response', callback)
  }

  /**
   * 订阅待处理的好友邀请列表
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeFriendInvitations(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/friend/invitations', callback)
  }

  /**
   * 请求订阅好友状态（触发后端发送好友列表）
   */
  requestFriendSubscribe(): void {
    this.send('/app/friend/subscribe', {})
  }

  /**
   * 发送游戏邀请给好友
   * @param friendId 好友ID
   * @param gameMode 游戏模式
   */
  sendFriendInvitation(friendId: number, gameMode: string = 'casual'): void {
    this.send('/app/friend/invite', { friendId, gameMode })
  }

  /**
   * 响应游戏邀请
   * @param invitationId 邀请ID
   * @param accept 是否接受
   * @param reason 拒绝原因（可选）
   */
  respondFriendInvitation(invitationId: number, accept: boolean, reason?: string): void {
    this.send('/app/friend/invite/respond', { invitationId, accept, reason })
  }

  /**
   * 取消游戏邀请
   * @param invitationId 邀请ID
   */
  cancelFriendInvitation(invitationId: number): void {
    this.send('/app/friend/invite/cancel', { invitationId })
  }

  /**
   * 获取待处理的邀请列表
   */
  getPendingInvitations(): void {
    this.send('/app/friend/invitations', {})
  }

  // ==================== 好友聊天相关 ====================

  /**
   * 发送私聊消息给好友
   * @param friendId 好友ID
   * @param content 消息内容
   */
  sendPrivateMessage(friendId: number, content: string): void {
    this.send('/app/chat/send', { friendId, content })
  }

  /**
   * 获取与好友的聊天历史
   * @param friendId 好友ID
   * @param limit 获取消息数量
   */
  getChatHistory(friendId: number, limit: number = 50): void {
    console.log('[WebSocket] 📜 请求聊天历史: friendId=', friendId, ', limit=', limit)
    this.send('/app/chat/history', { friendId, limit })
  }

  /**
   * 标记消息为已读
   * @param friendId 好友ID
   */
  markMessagesAsRead(friendId: number): void {
    this.send('/app/chat/read', { friendId })
  }

  /**
   * 获取未读消息列表
   */
  getUnreadMessages(): void {
    this.send('/app/chat/unread', {})
  }

  /**
   * 订阅私聊消息
   * @param userId 用户ID
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribePrivateMessage(userId: number, callback: SubscribeCallback): string {
    return this.subscribe('/topic/chat/private/' + userId, callback)
  }

  /**
   * 订阅聊天历史
   * @param userId 用户ID
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeChatHistory(userId: number, callback: SubscribeCallback): string {
    return this.subscribe('/topic/chat/history/' + userId, callback)
  }

  /**
   * 订阅未读消息
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeUnreadMessages(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/chat/unread', callback)
  }

  /**
   * 发送输入状态
   * @param friendId 好友ID
   * @param typing 是否正在输入
   */
  sendTypingStatus(friendId: number, typing: boolean): void {
    this.send('/app/chat/typing', { friendId, typing })
  }

  /**
   * 订阅好友输入状态
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeTypingStatus(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/chat/typing', callback)
  }

  // ==================== 观战系统相关 ====================

  /**
   * 加入观战
   * @param roomId 房间ID
   */
  joinObserver(roomId: string): void {
    this.send('/app/observer/join', { roomId })
  }

  /**
   * 离开观战
   * @param roomId 房间ID
   */
  leaveObserver(roomId: string): void {
    this.send('/app/observer/leave', { roomId })
  }

  /**
   * 获取可观战房间列表
   */
  getObservableRooms(): void {
    this.send('/app/observer/rooms', {})
  }

  /**
   * 订阅观战相关响应
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeObserverResponse(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/observer/response', callback)
  }

  /**
   * 订阅观战房间列表更新
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeObservableRooms(callback: SubscribeCallback): string {
    return this.subscribe('/user/queue/observer/rooms', callback)
  }

  /**
   * 订阅房间观战者数量变化
   * @param roomId 房间ID
   * @param callback 回调函数
   * @returns 订阅 ID
   */
  subscribeObserverCount(roomId: string, callback: SubscribeCallback): string {
    return this.subscribe(`/topic/room/${roomId}/observer`, callback)
  }
}

// 导出单例
export const wsClient = new WebSocketClient()

export default wsClient
