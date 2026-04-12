package com.gobang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对局记录服务实现
 */
@Service
@RequiredArgsConstructor
public class RecordServiceImpl implements RecordService {

    private static final Logger logger = LoggerFactory.getLogger(RecordServiceImpl.class);

    private final GameRecordMapper gameRecordMapper;

    @Override
    public Map<String, Object> getUserStats(Long userId) {
        logger.info("获取用户对局统计: userId={}", userId);

        // 获取最近3天的对局
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        List<GameRecord> recentRecords = gameRecordMapper.selectList(
                new LambdaQueryWrapper<GameRecord>()
                        .and(wrapper -> wrapper
                                .eq(GameRecord::getBlackPlayerId, userId)
                                .or()
                                .eq(GameRecord::getWhitePlayerId, userId)
                        )
                        .ge(GameRecord::getCreatedAt, threeDaysAgo)
        );

        // 获取总对局数
        List<GameRecord> allRecords = gameRecordMapper.selectList(
                new LambdaQueryWrapper<GameRecord>()
                        .and(wrapper -> wrapper
                                .eq(GameRecord::getBlackPlayerId, userId)
                                .or()
                                .eq(GameRecord::getWhitePlayerId, userId)
                        )
        );

        // 统计数据
        int totalGames = allRecords.size();
        int recentGames = recentRecords.size();

        int wins = 0;
        int losses = 0;
        int draws = 0;
        int blackWins = 0;
        int whiteWins = 0;

        for (GameRecord record : allRecords) {
            if (record.getWinnerId() == null) {
                draws++;
            } else if (record.getWinnerId().equals(userId)) {
                wins++;
                if (record.getWinColor() == 1) blackWins++;
                else whiteWins++;
            } else {
                losses++;
            }
        }

        // 最近3天统计
        int recentWins = 0;
        int recentLosses = 0;
        int recentDraws = 0;

        for (GameRecord record : recentRecords) {
            if (record.getWinnerId() == null) {
                recentDraws++;
            } else if (record.getWinnerId().equals(userId)) {
                recentWins++;
            } else {
                recentLosses++;
            }
        }

        double winRate = totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;
        double recentWinRate = recentGames > 0 ? (double) recentWins / recentGames * 100 : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGames", totalGames);
        stats.put("recentGames", recentGames);
        stats.put("wins", wins);
        stats.put("losses", losses);
        stats.put("draws", draws);
        stats.put("blackWins", blackWins);
        stats.put("whiteWins", whiteWins);
        stats.put("winRate", Math.round(winRate * 10.0) / 10.0);
        stats.put("recentWins", recentWins);
        stats.put("recentLosses", recentLosses);
        stats.put("recentDraws", recentDraws);
        stats.put("recentWinRate", Math.round(recentWinRate * 10.0) / 10.0);

        logger.info("用户对局统计: userId={}, 总场次={}, 胜率={}%", userId, totalGames, winRate);

        return stats;
    }
}
