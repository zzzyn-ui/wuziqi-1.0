package com.gobang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.FriendGroupMapper;
import com.gobang.mapper.FriendMapper;
import com.gobang.mapper.UserMapper;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.FriendGroup;
import com.gobang.model.entity.User;
import com.gobang.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友服务实现类
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private static final Logger logger = LoggerFactory.getLogger(FriendServiceImpl.class);

    private final FriendMapper friendMapper;
    private final FriendGroupMapper friendGroupMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public Friend sendRequest(Long userId, Long friendId, String message) {
        // 不能添加自己为好友
        if (userId.equals(friendId)) {
            throw new RuntimeException("不能添加自己为好友");
        }

        // 检查目标用户是否存在
        User targetUser = userMapper.selectById(friendId);
        if (targetUser == null) {
            throw new RuntimeException("用户不存在");
        }

        // 检查是否已经发送过请求或已是好友
        Friend existingFriend = friendMapper.findByUserAndFriend(userId, friendId);
        if (existingFriend != null) {
            if (existingFriend.getStatus() == 0) {
                throw new RuntimeException("已发送好友请求，等待对方确认");
            } else if (existingFriend.getStatus() == 1) {
                throw new RuntimeException("已经是好友关系");
            } else {
                // 之前的请求被拒绝或删除，可以重新发送
                existingFriend.setStatus(0);
                existingFriend.setRequestMessage(message);
                existingFriend.setUpdatedAt(LocalDateTime.now());
                friendMapper.updateEntity(existingFriend);
                logger.info("重新发送好友请求: userId={}, friendId={}", userId, friendId);
                return existingFriend;
            }
        }

        // 创建新的好友请求
        Friend friend = new Friend(userId, friendId);
        friend.setRequestMessage(message);
        friendMapper.insert(friend);

        logger.info("发送好友请求: userId={}, friendId={}, message={}", userId, friendId, message);
        return friend;
    }

    @Override
    @Transactional
    public boolean acceptRequest(Long userId, Long requestId) {
        Friend request = friendMapper.findById(requestId);
        if (request == null) {
            throw new RuntimeException("好友请求不存在");
        }

        // 验证：请求是发给当前用户的
        if (!request.getFriendId().equals(userId)) {
            throw new RuntimeException("无权操作此请求");
        }

        // 验证：请求状态是待确认
        if (request.getStatus() != 0) {
            throw new RuntimeException("请求已被处理");
        }

        // 接受请求：创建双向好友关系
        request.setStatus(1);
        friendMapper.updateStatus(requestId, 1);

        // 创建反向好友关系
        Friend reverseFriend = new Friend(request.getFriendId(), request.getUserId());
        reverseFriend.setStatus(1);
        friendMapper.insert(reverseFriend);

        logger.info("接受好友请求: userId={}, requestId={}", userId, requestId);
        return true;
    }

    @Override
    @Transactional
    public boolean rejectRequest(Long userId, Long requestId) {
        Friend request = friendMapper.findById(requestId);
        if (request == null) {
            throw new RuntimeException("好友请求不存在");
        }

        // 验证：请求是发给当前用户的
        if (!request.getFriendId().equals(userId)) {
            throw new RuntimeException("无权操作此请求");
        }

        // 拒绝请求：更新状态为已拒绝
        request.setStatus(2);
        friendMapper.updateStatus(requestId, 2);

        logger.info("拒绝好友请求: userId={}, requestId={}", userId, requestId);
        return true;
    }

    @Override
    public List<Map<String, Object>> getFriendList(Long userId) {
        // 获取所有已接受的好友关系
        List<Friend> friends = friendMapper.findFriendsByUserId(userId);

        // 获取好友的详细信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friend friend : friends) {
            User user = userMapper.selectById(friend.getFriendId());
            if (user != null) {
                Map<String, Object> friendInfo = new HashMap<>();
                friendInfo.put("id", friend.getId());
                friendInfo.put("userId", user.getId());
                friendInfo.put("username", user.getUsername());
                friendInfo.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                friendInfo.put("avatar", user.getAvatar());
                friendInfo.put("level", user.getLevel());
                friendInfo.put("rating", user.getRating());
                friendInfo.put("online", user.isOnline() ||
                    (user.getLastOnline() != null && user.getLastOnline().isAfter(LocalDateTime.now().minusMinutes(5))));
                friendInfo.put("remark", friend.getRemark());
                friendInfo.put("groupId", friend.getGroupId());
                friendInfo.put("createdAt", friend.getCreatedAt());
                result.add(friendInfo);
            }
        }

        logger.info("获取好友列表: userId={}, count={}", userId, result.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> getPendingRequests(Long userId) {
        // 获取所有待处理的好友请求
        List<Friend> requests = friendMapper.findPendingRequests(userId);

        // 获取请求者的详细信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (Friend request : requests) {
            User user = userMapper.selectById(request.getUserId());
            if (user != null) {
                Map<String, Object> requestInfo = new HashMap<>();
                requestInfo.put("id", request.getId());
                requestInfo.put("userId", user.getId());
                requestInfo.put("username", user.getUsername());
                requestInfo.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                requestInfo.put("avatar", user.getAvatar());
                requestInfo.put("level", user.getLevel());
                requestInfo.put("rating", user.getRating());
                requestInfo.put("message", request.getRequestMessage());
                requestInfo.put("createdAt", request.getCreatedAt());
                result.add(requestInfo);
            }
        }

        logger.info("获取待处理请求: userId={}, count={}", userId, result.size());
        return result;
    }

    @Override
    @Transactional
    public boolean deleteFriend(Long userId, Long friendId) {
        // 删除双向好友关系
        int count1 = friendMapper.delete(userId, friendId);
        int count2 = friendMapper.delete(friendId, userId);

        logger.info("删除好友: userId={}, friendId={}, deleted={}", userId, friendId, count1 + count2);
        return count1 > 0 || count2 > 0;
    }

    @Override
    public boolean updateRemark(Long userId, Long friendId, String remark) {
        int count = friendMapper.updateRemark(userId, friendId, remark);
        logger.info("更新好友备注: userId={}, friendId={}, remark={}", userId, friendId, remark);
        return count > 0;
    }

    @Override
    public boolean isFriend(Long userId, Long friendId) {
        int count = friendMapper.isFriend(userId, friendId);
        return count > 0;
    }

    @Override
    public List<Map<String, Object>> searchUsers(Long userId, String keyword) {
        // 搜索用户（排除自己和已是好友的用户）
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(User::getUsername, keyword)
               .ne(User::getId, userId)
               .last("LIMIT 20"); // 限制返回数量

        List<User> users = userMapper.selectList(wrapper);

        // 转换为返回格式，标记是否已是好友
        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("level", user.getLevel());
            userInfo.put("rating", user.getRating());
            userInfo.put("online", user.isOnline() ||
                (user.getLastOnline() != null && user.getLastOnline().isAfter(LocalDateTime.now().minusMinutes(5))));
            userInfo.put("isFriend", isFriend(userId, user.getId()));
            return userInfo;
        }).collect(Collectors.toList());

        logger.info("搜索用户: userId={}, keyword={}, count={}", userId, keyword, result.size());
        return result;
    }

    @Override
    public List<FriendGroup> getGroups(Long userId) {
        return friendGroupMapper.getByUserId(userId);
    }

    @Override
    @Transactional
    public FriendGroup createGroup(Long userId, String groupName) {
        // 检查分组数量限制
        List<FriendGroup> groups = friendGroupMapper.getByUserId(userId);
        if (groups.size() >= 20) {
            throw new RuntimeException("分组数量已达上限（20个）");
        }

        FriendGroup group = new FriendGroup(userId, groupName);
        friendGroupMapper.insert(group);

        logger.info("创建好友分组: userId={}, groupName={}", userId, groupName);
        return group;
    }

    @Override
    @Transactional
    public boolean deleteGroup(Long userId, Integer groupId) {
        FriendGroup group = friendGroupMapper.getById(groupId);
        if (group == null) {
            throw new RuntimeException("分组不存在");
        }

        if (!group.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此分组");
        }

        // 将该分组下的好友移到默认分组
        friendMapper.moveFriendsToDefaultGroup(userId, groupId);

        // 删除分组
        int count = friendGroupMapper.delete(groupId);

        logger.info("删除好友分组: userId={}, groupId={}", userId, groupId);
        return count > 0;
    }

    @Override
    public boolean moveFriendToGroup(Long userId, Long friendId, Integer groupId) {
        Friend friend = friendMapper.findByUserAndFriend(userId, friendId);
        if (friend == null) {
            throw new RuntimeException("好友关系不存在");
        }

        int count = friendMapper.updateGroup(friend.getId(), groupId);

        logger.info("移动好友到分组: userId={}, friendId={}, groupId={}", userId, friendId, groupId);
        return count > 0;
    }

    @Override
    public int getOnlineFriendCount(Long userId) {
        List<Map<String, Object>> friends = getFriendList(userId);
        return (int) friends.stream()
                .filter(f -> (Boolean) f.get("online"))
                .count();
    }
}
