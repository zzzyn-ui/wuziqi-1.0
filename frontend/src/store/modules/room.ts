import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RoomInfo, RoomListItem } from '@/types/game'

export const useRoomStore = defineStore('room', () => {
  // 状态
  const roomId = ref<string>('')
  const roomInfo = ref<RoomInfo | null>(null)
  const roomList = ref<RoomListItem[]>([])
  const currentRoomPage = ref<number>(1)
  const roomPageSize = ref<number>(10)
  const roomTotal = ref<number>(0)

  // 计算属性
  const isInRoom = computed<boolean>(() => {
    return !!roomId.value && !!roomInfo.value
  })

  const isRoomHost = computed<boolean>(() => {
    return !!roomInfo.value && roomInfo.value.host.id === getCurrentUserId()
  })

  const canStartGame = computed<boolean>(() => {
    return (
      isInRoom.value &&
      isRoomHost.value &&
      roomInfo.value!.currentPlayerCount === roomInfo.value!.maxPlayers &&
      roomInfo.value!.status === 'waiting'
    )
  })

  /**
   * 获取当前用户 ID
   * @returns number
   */
  const getCurrentUserId = (): number => {
    // TODO: 从 user store 获取
    const userInfo = localStorage.getItem('userInfo')
    if (userInfo) {
      try {
        return JSON.parse(userInfo).id
      } catch {
        return 0
      }
    }
    return 0
  }

  /**
   * 设置当前房间 ID
   * @param id 房间 ID
   */
  const setRoomId = (id: string): void => {
    roomId.value = id
  }

  /**
   * 设置房间信息
   * @param info 房间信息
   */
  const setRoomInfo = (info: RoomInfo): void => {
    roomInfo.value = info
    roomId.value = info.id
  }

  /**
   * 设置房间列表
   * @param list 房间列表
   */
  const setRoomList = (list: RoomListItem[]): void => {
    roomList.value = list
  }

  /**
   * 添加房间到列表
   * @param room 房间信息
   */
  const addRoomToList = (room: RoomListItem): void => {
    roomList.value.unshift(room)
  }

  /**
   * 从列表中移除房间
   * @param roomId 房间 ID
   */
  const removeRoomFromList = (roomId: string): void => {
    const index = roomList.value.findIndex((r) => r.id === roomId)
    if (index > -1) {
      roomList.value.splice(index, 1)
    }
  }

  /**
   * 更新列表中的房间信息
   * @param roomId 房间 ID
   * @param updates 更新内容
   */
  const updateRoomInList = (roomId: string, updates: Partial<RoomListItem>): void => {
    const room = roomList.value.find((r) => r.id === roomId)
    if (room) {
      Object.assign(room, updates)
    }
  }

  /**
   * 设置当前房间列表页码
   * @param page 页码
   */
  const setCurrentPage = (page: number): void => {
    currentRoomPage.value = page
  }

  /**
   * 设置房间列表每页数量
   * @param size 每页数量
   */
  const setPageSize = (size: number): void => {
    roomPageSize.value = size
  }

  /**
   * 设置房间总数
   * @param total 总数
   */
  const setRoomTotal = (total: number): void => {
    roomTotal.value = total
  }

  /**
   * 清空房间信息
   */
  const clearRoomInfo = (): void => {
    roomId.value = ''
    roomInfo.value = null
  }

  /**
   * 清空房间列表
   */
  const clearRoomList = (): void => {
    roomList.value = []
    currentRoomPage.value = 1
    roomTotal.value = 0
  }

  /**
   * 离开房间
   */
  const leaveRoom = (): void => {
    roomId.value = ''
    roomInfo.value = null
  }

  return {
    // 状态
    roomId,
    roomInfo,
    roomList,
    currentRoomPage,
    roomPageSize,
    roomTotal,
    // 计算属性
    isInRoom,
    isRoomHost,
    canStartGame,
    // 方法
    getCurrentUserId,
    setRoomId,
    setRoomInfo,
    setRoomList,
    addRoomToList,
    removeRoomFromList,
    updateRoomInList,
    setCurrentPage,
    setPageSize,
    setRoomTotal,
    clearRoomInfo,
    clearRoomList,
    leaveRoom,
  }
})

// 导出类型
export type RoomStore = ReturnType<typeof useRoomStore>
