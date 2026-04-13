package com.gobang.service;

import java.util.Map;

/**
 * 对局记录服务接口
 */
public interface RecordService {

    /**
     * 获取用户对局统计数据
     *
     * @param userId 用户ID
     * @return 统计数据
     */
    Map<String, Object> getUserStats(Long userId);
}
