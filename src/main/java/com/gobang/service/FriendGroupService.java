package com.gobang.service;

import com.gobang.mapper.FriendGroupMapper;
import com.gobang.mapper.FriendMapper;
import com.gobang.model.entity.Friend;
import com.gobang.model.entity.FriendGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 好友分组服务
 */
@Service
public class FriendGroupService {

    private static final Logger logger = LoggerFactory.getLogger(FriendGroupService.class);

    private final FriendGroupMapper friendGroupMapper;
    private final FriendMapper friendMapper;

    public FriendGroupService(FriendGroupMapper friendGroupMapper, FriendMapper friendMapper) {
        this.friendGroupMapper = friendGroupMapper;
        this.friendMapper = friendMapper;
    }

    /**
     * 获取用户的所有分组
     */
    public List<FriendGroup> getUserGroups(Long userId) {
        List<FriendGroup> groups = friendGroupMapper.getByUserId(userId);

        // 如果用户没有任何分组，创建默认分组
        if (groups.isEmpty()) {
            createDefaultGroups(userId);
            groups = friendGroupMapper.getByUserId(userId);
        }

        return groups;
    }

    /**
     * 创建默认分组
     */
    private void createDefaultGroups(Long userId) {
        String[] defaultGroupNames = {"我的好友", "游戏好友", "家人"};

        for (String groupName : defaultGroupNames) {
            FriendGroup group = new FriendGroup(userId, groupName);
            group.setSortOrder(friendGroupMapper.getNextSortOrder(userId));
            friendGroupMapper.insert(group);
        }

        logger.info("Created default groups for user: {}", userId);
    }

    /**
     * 创建新分组
     */
    public FriendGroup createGroup(Long userId, String groupName) {
        // 检查分组名是否已存在
        FriendGroup existing = friendGroupMapper.getByUserIdAndName(userId, groupName);
        if (existing != null) {
            throw new IllegalArgumentException("分组名已存在");
        }

        FriendGroup group = new FriendGroup(userId, groupName);
        group.setSortOrder(friendGroupMapper.getNextSortOrder(userId));
        friendGroupMapper.insert(group);

        logger.info("User {} created group: {}", userId, groupName);
        return group;
    }

    /**
     * 重命名分组
     */
    public void renameGroup(Integer groupId, Long userId, String newName) {
        FriendGroup group = friendGroupMapper.getById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("分组不存在");
        }

        if (!group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限操作此分组");
        }

        // 检查新名称是否已存在
        FriendGroup existing = friendGroupMapper.getByUserIdAndName(userId, newName);
        if (existing != null && !existing.getId().equals(groupId)) {
            throw new IllegalArgumentException("分组名已存在");
        }

        group.setGroupName(newName);
        friendGroupMapper.updateName(group);

        logger.info("User {} renamed group {} to {}", userId, groupId, newName);
    }

    /**
     * 删除分组
     */
    public void deleteGroup(Integer groupId, Long userId) {
        FriendGroup group = friendGroupMapper.getById(groupId);
        if (group == null) {
            throw new IllegalArgumentException("分组不存在");
        }

        if (!group.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限操作此分组");
        }

        // 将该分组下的好友移到默认分组（"我的好友"）
        List<Friend> friendsInGroup = friendMapper.getByUserIdAndGroupId(userId, groupId);
        FriendGroup defaultGroup = friendGroupMapper.getByUserIdAndName(userId, "我的好友");

        if (defaultGroup != null) {
            for (Friend friend : friendsInGroup) {
                friend.setGroupId(defaultGroup.getId());
                friendMapper.updateEntity(friend);
            }
        }

        friendGroupMapper.delete(groupId);

        logger.info("User {} deleted group: {}", userId, groupId);
    }

    /**
     * 更新好友分组
     */
    public void updateFriendGroup(Long userId, Long friendId, Integer newGroupId) {
        Friend friend = friendMapper.getByUserIdAndFriendId(userId, friendId);
        if (friend == null) {
            throw new IllegalArgumentException("好友关系不存在");
        }

        // 验证新分组是否属于当前用户
        if (newGroupId != null) {
            FriendGroup group = friendGroupMapper.getById(newGroupId);
            if (group == null || !group.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无效的分组");
            }
        }

        friend.setGroupId(newGroupId);
        friendMapper.updateEntity(friend);

        logger.info("User {} moved friend {} to group {}", userId, friendId, newGroupId);
    }
}
