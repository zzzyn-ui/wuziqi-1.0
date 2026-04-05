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
export type WSMessageType = 'game.start' | 'game.move' | 'game.end' | 'player.join' | 'player.leave'

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

  /**
   * 连接 WebSocket
   * @param token 认证 token
   * @returns Promise<boolean>
   */
  connect(token: string): Promise<boolean> {
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
   */
  send<T = any>(destination: string, body: T): void {
    if (!this.client || !this.connected) {
      ElMessage.error('WebSocket 未连接')
      return
    }

    try {
      this.client.publish({
        destination,
        body: JSON.stringify(body),
      })
    } catch (error) {
      console.error('[WebSocket] Send error:', error)
      ElMessage.error('发送消息失败')
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
          console.log('[WebSocket] 收到订阅消息:', destination, message.body)
          const data = JSON.parse(message.body) as T
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
}

// 导出单例
export const wsClient = new WebSocketClient()

export default wsClient
