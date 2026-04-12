# 观战系统备份

## 备份日期
2026-04-10

## 系统概述
观战系统允许用户实时观看其他玩家的对局。

## 后端文件结构

### Controller
- `ObserverController.java` - 观战功能控制器
  - POST `/app/observer/join` - 加入观战
  - POST `/app/observer/leave` - 离开观战
  - GET `/app/observer/rooms` - 获取可观战房间列表

### Service
- `ObserverService.java` - 观战服务接口
  - `joinObserverRoom(Long userId, String roomId)` - 加入观战房间
  - `leaveObserverRoom(Long userId, String roomId)` - 离开观战房间
  - `getObservableRooms()` - 获取可观战房间列表
  - `getObserverCount(String roomId)` - 获取房间观战人数

- `ObserverServiceImpl.java` - 观战服务实现
  - 使用 `RoomManager` 管理观战者列表
  - 广播观战者数量变化
  - 处理观战者加入/离开逻辑

### DTO
- `ObserverRoomDto.java` - 观战房间数据传输对象
  - `roomId` - 房间ID
  - `observerCount` - 当前观战人数
  - `players` - 对局玩家信息

## 前端文件结构

### Views
- `ObserverView.vue` - 观战页面组件
  - 观战房间列表
  - 实时观战界面
  - 观战者数量显示

## WebSocket 订阅

### 客户端订阅
- `/user/queue/observer/response` - 观战操作响应
- `/user/queue/observer/rooms` - 可观战房间列表
- `/topic/room/{roomId}/observer` - 房间观战者数量变化

### 消息类型
- `OBSERVER_JOIN` - 加入观战成功
- `OBSERVER_LEAVE` - 离开观战成功
- `OBSERVER_COUNT_UPDATE` - 观战人数更新
- `OBSERVABLE_ROOMS` - 可观战房间列表

## 数据库表
- 使用现有的游戏房间，无需额外表
- 观战者信息存储在内存中（RoomManager）

## 核心功能
1. 用户可以查看当前进行中的对局列表
2. 点击观战按钮进入观战模式
3. 实时查看对局进度
4. 显示当前观战人数
5. 随时可以退出观战

## 依赖关系
- `RoomManager` - 房间管理器
- `SimpMessagingTemplate` - WebSocket 消息发送
- `GameService` - 游戏服务（获取对局信息）

## 注意事项
- 观战者不能参与对局，只能观看
- 观战者离开不影响对局进行
- 观战者数量实时更新
