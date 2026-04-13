package com.gobang.mapper;

import com.gobang.model.entity.PuzzleRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 残局通关记录 Mapper
 */
@Mapper
public interface PuzzleRecordMapper {

    // 根据用户ID查询所有记录
    @Select("SELECT pr.*, p.title as puzzle_title, p.difficulty as puzzle_difficulty " +
            "FROM puzzle_records pr " +
            "JOIN puzzles p ON pr.puzzle_id = p.id " +
            "WHERE pr.user_id = #{userId} " +
            "ORDER BY pr.updated_at DESC")
    List<PuzzleRecord> findByUserId(@Param("userId") Long userId);

    // 根据用户ID和残局ID查询记录
    @Select("SELECT pr.*, p.title as puzzle_title, p.difficulty as puzzle_difficulty " +
            "FROM puzzle_records pr " +
            "JOIN puzzles p ON pr.puzzle_id = p.id " +
            "WHERE pr.user_id = #{userId} AND pr.puzzle_id = #{puzzleId}")
    PuzzleRecord findByUserAndPuzzle(@Param("userId") Long userId, @Param("puzzleId") Long puzzleId);

    // 根据用户ID和难度查询记录
    @Select("SELECT pr.*, p.title as puzzle_title, p.difficulty as puzzle_difficulty " +
            "FROM puzzle_records pr " +
            "JOIN puzzles p ON pr.puzzle_id = p.id " +
            "WHERE pr.user_id = #{userId} AND p.difficulty = #{difficulty} " +
            "ORDER BY p.level_order")
    List<PuzzleRecord> findByUserIdAndDifficulty(@Param("userId") Long userId, @Param("difficulty") String difficulty);

    // 插入记录
    @Insert("INSERT INTO puzzle_records (user_id, puzzle_id, attempts, completed, " +
            "best_moves, best_time, stars, solution_path, completed_at) " +
            "VALUES (#{userId}, #{puzzleId}, #{attempts}, #{completed}, " +
            "#{bestMoves}, #{bestTime}, #{stars}, #{solutionPath}, #{completedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PuzzleRecord record);

    // 更新记录
    @Update("UPDATE puzzle_records SET attempts = #{attempts}, completed = #{completed}, " +
            "best_moves = #{bestMoves}, best_time = #{bestTime}, stars = #{stars}, " +
            "solution_path = #{solutionPath}, completed_at = #{completedAt} " +
            "WHERE user_id = #{userId} AND puzzle_id = #{puzzleId}")
    int update(PuzzleRecord record);

    // 增加尝试次数
    @Update("UPDATE puzzle_records SET attempts = attempts + 1 " +
            "WHERE user_id = #{userId} AND puzzle_id = #{puzzleId}")
    int incrementAttempts(@Param("userId") Long userId, @Param("puzzleId") Long puzzleId);

    // 删除记录
    @Delete("DELETE FROM puzzle_records WHERE user_id = #{userId} AND puzzle_id = #{puzzleId}")
    int delete(@Param("userId") Long userId, @Param("puzzleId") Long puzzleId);

    // 统计用户完成情况
    @Select("SELECT " +
            "SUM(CASE WHEN completed THEN 1 ELSE 0 END) as completed_count, " +
            "SUM(CASE WHEN stars = 3 THEN 1 ELSE 0 END) as three_star_count, " +
            "SUM(attempts) as total_attempts " +
            "FROM puzzle_records WHERE user_id = #{userId}")
    java.util.Map<String, Object> getUserStats(@Param("userId") Long userId);

    // 获取用户排行榜（按完成残局数排序）
    @Select("SELECT user_id, " +
            "SUM(CASE WHEN completed THEN 1 ELSE 0 END) as completed_count, " +
            "SUM(CASE WHEN stars = 3 THEN 1 ELSE 0 END) as three_star_count " +
            "FROM puzzle_records " +
            "GROUP BY user_id " +
            "ORDER BY completed_count DESC, three_star_count DESC " +
            "LIMIT #{limit}")
    List<java.util.Map<String, Object>> getLeaderboard(@Param("limit") int limit);
}
