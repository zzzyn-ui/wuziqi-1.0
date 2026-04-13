package com.gobang.mapper;

import org.apache.ibatis.annotations.*;

import java.util.Map;

/**
 * 残局统计 Mapper
 */
@Mapper
public interface PuzzleStatsMapper {

    // 根据残局ID查询统计
    @Select("SELECT * FROM puzzle_stats WHERE puzzle_id = #{puzzleId}")
    Map<String, Object> findByPuzzleId(@Param("puzzleId") Long puzzleId);

    // 插入统计
    @Insert("INSERT INTO puzzle_stats (puzzle_id, total_attempts, total_completions, " +
            "avg_moves, avg_time, completion_rate, one_star_count, two_star_count, three_star_count) " +
            "VALUES (#{puzzleId}, #{totalAttempts}, #{totalCompletions}, #{avgMoves}, " +
            "#{avgTime}, #{completionRate}, #{oneStarCount}, #{twoStarCount}, #{threeStarCount})")
    int insert(Map<String, Object> stats);

    // 更新统计
    @Update("UPDATE puzzle_stats SET " +
            "total_attempts = #{totalAttempts}, " +
            "total_completions = #{totalCompletions}, " +
            "avg_moves = #{avgMoves}, " +
            "avg_time = #{avgTime}, " +
            "completion_rate = #{completionRate}, " +
            "one_star_count = #{oneStarCount}, " +
            "two_star_count = #{twoStarCount}, " +
            "three_star_count = #{threeStarCount} " +
            "WHERE puzzle_id = #{puzzleId}")
    int update(Map<String, Object> stats);

    // 增加尝试次数
    @Update("UPDATE puzzle_stats SET total_attempts = total_attempts + 1 " +
            "WHERE puzzle_id = #{puzzleId}")
    int incrementAttempts(@Param("puzzleId") Long puzzleId);

    // 增加完成次数
    @Update("UPDATE puzzle_stats SET total_completions = total_completions + 1 " +
            "WHERE puzzle_id = #{puzzleId}")
    int incrementCompletions(@Param("puzzleId") Long puzzleId);

    // 更新星级统计
    @Update("UPDATE puzzle_stats SET " +
            "one_star_count = one_star_count + CASE WHEN #{stars} = 1 THEN 1 ELSE 0 END, " +
            "two_star_count = two_star_count + CASE WHEN #{stars} = 2 THEN 1 ELSE 0 END, " +
            "three_star_count = three_star_count + CASE WHEN #{stars} = 3 THEN 1 ELSE 0 END " +
            "WHERE puzzle_id = #{puzzleId}")
    int updateStarCount(@Param("puzzleId") Long puzzleId, @Param("stars") int stars);

    // 重新计算完成率
    @Update("UPDATE puzzle_stats SET " +
            "completion_rate = CASE WHEN total_attempts > 0 THEN " +
            "ROUND(total_completions * 100.0 / total_attempts, 2) ELSE 0 END " +
            "WHERE puzzle_id = #{puzzleId}")
    int recalculateCompletionRate(@Param("puzzleId") Long puzzleId);

    // 更新平均步数和平均时间
    @Update("UPDATE puzzle_stats SET " +
            "avg_moves = #{avgMoves}, " +
            "avg_time = #{avgTime} " +
            "WHERE puzzle_id = #{puzzleId}")
    int updateAverages(@Param("puzzleId") Long puzzleId,
                      @Param("avgMoves") double avgMoves,
                      @Param("avgTime") double avgTime);

    // 确保统计记录存在
    @Insert("INSERT IGNORE INTO puzzle_stats (puzzle_id, total_attempts, total_completions, " +
            "completion_rate) VALUES (#{puzzleId}, 0, 0, 0)")
    int ensureExists(@Param("puzzleId") Long puzzleId);
}
