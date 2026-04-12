package com.gobang.service;

import com.gobang.model.dto.ObserverRoomDto;

import java.util.List;
import java.util.Map;

/**
 * 观战服务接口
 * 处理观战模式的业务逻辑
 */
public interface ObserverService {

    /**
     * 获取可观的战房间列表
     * @return 可观的房间列表
     */
    List<Map<String, Object>> getObservableRooms();

    /**
     * 加入观战
     * @param roomId 房间ID
     * @param userId 观战者用户ID
     * @return 观战房间信息
     */
    ObserverRoomDto joinObserver(String roomId, Long userId);

    /**
     * 离开观战
     * @param roomId 房间ID
     * @param userId 观战者用户ID
     */
    void leaveObserver(String roomId, Long userId);

    /**
     * 获取房间观战者列表
     * @param roomId 房间ID
     * @return 观战者ID列表
     */
    List<Long> getObservers(String roomId);

    /**
     * 获取房间观战者数量
     * @param roomId 房间ID
     * @return 观战者数量
     */
    int getObserverCount(String roomId);

    /**
     * 检查用户是否正在观战某房间
     * @param userId 用户ID
     * @return 房间ID，如果未观战则返回null
     */
    String getObservingRoom(Long userId);
}
