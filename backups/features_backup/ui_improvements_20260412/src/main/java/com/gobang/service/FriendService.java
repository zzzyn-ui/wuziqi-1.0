package com.gobang.service;

import com.gobang.model.entity.Friend;
import com.gobang.model.entity.FriendGroup;

import java.util.List;
import java.util.Map;

/**
 * 好友服务接口
 */
public interface FriendService {

    /**
     * 发送好友请求
     * @param userId 发起请求的用户ID
     * @param friendId 目标用户ID
     * @param message 请求消息
     * @return Friend
     */
    Friend sendRequest(Long userId, Long friendId, String message);

    /**
     * 接受好友请求
     * @param userId 接受请求的用户ID
     * @param requestId 好友关系ID
     * @return 是否成功
     */
    boolean acceptRequest(Long userId, Long requestId);

    /**
     * 拒绝好友请求
     * @param userId 拒绝请求的用户ID
     * @param requestId 好友关系ID
     * @return 是否成功
     */
    boolean rejectRequest(Long userId, Long requestId);

    /**
     * 获取好友列表
     * @param userId 用户ID
     * @return 好友列表（包含好友详细信息）
     */
    List<Map<String, Object>> getFriendList(Long userId);

    /**
     * 获取待处理的好友请求
     * @param userId 用户ID
     * @return 待处理的请求列表
     */
    List<Map<String, Object>> getPendingRequests(Long userId);

    /**
     * 删除好友
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 是否成功
     */
    boolean deleteFriend(Long userId, Long friendId);

    /**
     * 更新好友备注
     * @param userId 用户ID
     * @param friendId 好友ID
     * @param remark 备注
     * @return 是否成功
     */
    boolean updateRemark(Long userId, Long friendId, String remark);

    /**
     * 检查是否是好友
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 是否是好友
     */
    boolean isFriend(Long userId, Long friendId);

    /**
     * 搜索用户（用于添加好友）
     * @param userId 当前用户ID
     * @param keyword 搜索关键词（用户名）
     * @return 用户列表
     */
    List<Map<String, Object>> searchUsers(Long userId, String keyword);

    /**
     * 获取好友分组列表
     * @param userId 用户ID
     * @return 分组列表
     */
    List<FriendGroup> getGroups(Long userId);

    /**
     * 创建好友分组
     * @param userId 用户ID
     * @param groupName 分组名称
     * @return 创建的分组
     */
    FriendGroup createGroup(Long userId, String groupName);

    /**
     * 删除好友分组
     * @param userId 用户ID
     * @param groupId 分组ID
     * @return 是否成功
     */
    boolean deleteGroup(Long userId, Integer groupId);

    /**
     * 移动好友到分组
     * @param userId 用户ID
     * @param friendId 好友ID
     * @param groupId 分组ID
     * @return 是否成功
     */
    boolean moveFriendToGroup(Long userId, Long friendId, Integer groupId);

    /**
     * 获取在线好友数量
     * @param userId 用户ID
     * @return 在线好友数量
     */
    int getOnlineFriendCount(Long userId);
}
