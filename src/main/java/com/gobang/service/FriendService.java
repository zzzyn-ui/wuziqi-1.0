package com.gobang.service;

import com.gobang.mapper.FriendMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.User;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 好友服务
 */
public class FriendService {

    private static final Logger logger = LoggerFactory.getLogger(FriendService.class);

    private final SqlSessionFactory sqlSessionFactory;

    public FriendService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 发送好友请求
     */
    public boolean sendRequest(Long userId, Long targetId, String message) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);

            // 检查是否已是好友（只检查状态为1的好友关系）
            int friendCount = friendMapper.isFriend(userId, targetId);
            if (friendCount > 0) {
                return false;
            }

            // 检查是否已有请求
            Friend existing = friendMapper.findByUserAndFriend(userId, targetId);
            if (existing != null) {
                // 如果请求已被拒绝（status=-1）或过期，删除旧记录并允许重新发送
                if (existing.getStatus() == -1) {
                    friendMapper.delete(userId, targetId);
                } else if (existing.getStatus() == 0) {
                    // 待处理的请求，不允许重复发送
                    return false;
                } else {
                    // 已是好友关系
                    return false;
                }
            }

            Friend friend = new Friend(userId, targetId);
            friend.setRequestMessage(message);
            friend.setStatus(0);

            int result = friendMapper.insert(friend);
            session.commit();
            return result > 0;
        }
    }

    /**
     * 接受好友请求
     */
    public boolean acceptRequest(Long requestId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            Friend request = friendMapper.findById(requestId);

            if (request == null) {
                logger.warn("Friend request not found: {}", requestId);
                return false;
            }

            if (!request.isPending()) {
                logger.warn("Friend request {} is not pending, current status: {}", requestId, request.getStatus());
                return false;
            }

            int result = friendMapper.updateStatus(request.getId(), 1);

            // 创建双向关系
            Friend reverse = new Friend(request.getFriendId(), request.getUserId());
            reverse.setStatus(1);
            int reverseResult = friendMapper.insert(reverse);

            session.commit();
            return result > 0;
        } catch (Exception e) {
            logger.error("Error accepting friend request: {}", requestId, e);
            return false;
        }
    }

    /**
     * 拒绝好友请求
     */
    public boolean rejectRequest(Long requestId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            Friend request = friendMapper.findById(requestId);
            if (request == null) {
                return false;
            }

            int result = friendMapper.updateStatus(request.getId(), -1);
            session.commit();
            return result > 0;
        }
    }

    /**
     * 删除好友
     */
    public boolean removeFriend(Long userId, Long friendId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            friendMapper.delete(userId, friendId);
            friendMapper.delete(friendId, userId);
            return true;
        }
    }

    /**
     * 获取好友列表（双向查询）
     */
    public List<User> getFriendList(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);

            // 查询双向好友关系
            // 1. 当前用户发起的好友关系（user_id = 当前用户）
            List<Friend> friendsAsUser = friendMapper.findFriendsByUserId(userId);
            // 2. 其他用户发起的好友关系（friend_id = 当前用户）
            List<Friend> friendsAsFriend = friendMapper.findReverseFriends(userId);

            // 合并并去重
            java.util.Set<Long> friendIds = new java.util.HashSet<>();
            for (Friend f : friendsAsUser) {
                friendIds.add(f.getFriendId());
            }
            for (Friend f : friendsAsFriend) {
                friendIds.add(f.getUserId());
            }

            // 移除自己
            friendIds.remove(userId);

            List<User> users = new ArrayList<>();
            for (Long friendId : friendIds) {
                User user = userMapper.findById(friendId);
                if (user != null) {
                    users.add(user);
                }
            }

            return users;
        }
    }

    /**
     * 获取待处理的好友请求
     */
    public List<Friend> getPendingRequests(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            return friendMapper.findPendingRequests(userId);
        }
    }

    /**
     * 设置好友备注
     */
    public boolean setFriendRemark(Long userId, Long friendId, String remark) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);

            // 检查是否是好友
            if (friendMapper.isFriend(userId, friendId) == 0) {
                return false;
            }

            int result = friendMapper.updateRemark(userId, friendId, remark);
            session.commit();
            return result > 0;
        }
    }

    /**
     * 移动好友到分组
     */
    public boolean moveFriendToGroup(Long userId, Long friendId, Integer groupId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);

            Friend friend = friendMapper.getByUserIdAndFriendId(userId, friendId);
            if (friend == null) {
                return false;
            }

            friend.setGroupId(groupId);
            int result = friendMapper.updateEntity(friend);
            session.commit();
            return result > 0;
        }
    }

    /**
     * 获取好友详细信息（包含备注）
     */
    public Friend getFriendDetail(Long userId, Long friendId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            return friendMapper.getByUserIdAndFriendId(userId, friendId);
        }
    }
}
