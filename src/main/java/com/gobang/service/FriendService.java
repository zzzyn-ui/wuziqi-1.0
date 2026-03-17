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

            // 检查是否已是好友
            if (friendMapper.isFriend(userId, targetId) > 0) {
                return false;
            }

            // 检查是否已有请求
            Friend existing = friendMapper.findByUserAndFriend(userId, targetId);
            if (existing != null) {
                return false;
            }

            Friend friend = new Friend(userId, targetId);
            friend.setRequestMessage(message);
            friend.setStatus(0);

            int result = friendMapper.insert(friend);
            session.commit();
            logger.info("Friend request from {} to {}", userId, targetId);
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
            if (request == null || !request.isPending()) {
                return false;
            }

            int result = friendMapper.updateStatus(request.getId(), 1);

            // 创建双向关系
            Friend reverse = new Friend(request.getFriendId(), request.getUserId());
            reverse.setStatus(1);
            friendMapper.insert(reverse);

            session.commit();
            logger.info("Friend accepted: {} <-> {}", request.getUserId(), request.getFriendId());
            return result > 0;
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
            logger.info("Friend request rejected: {} -> {}", request.getUserId(), request.getFriendId());
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
            logger.info("Friend removed: {} <-> {}", userId, friendId);
            return true;
        }
    }

    /**
     * 获取好友列表
     */
    public List<User> getFriendList(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            FriendMapper friendMapper = session.getMapper(FriendMapper.class);
            UserMapper userMapper = session.getMapper(UserMapper.class);
            List<Friend> friends = friendMapper.findFriendsByUserId(userId);
            List<User> users = new ArrayList<>();

            for (Friend friend : friends) {
                User user = userMapper.findById(friend.getFriendId());
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
}
