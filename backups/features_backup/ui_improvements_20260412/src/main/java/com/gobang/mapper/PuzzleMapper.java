package com.gobang.mapper;

import com.gobang.model.entity.Puzzle;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 残局 Mapper
 */
@Mapper
public interface PuzzleMapper {

    // 根据ID查询残局
    @Select("SELECT p.*, " +
            "COALESCE(s.total_attempts, 0) as total_attempts, " +
            "COALESCE(s.total_completions, 0) as total_completions, " +
            "COALESCE(s.completion_rate, 0) as completion_rate, " +
            "COALESCE(s.three_star_count, 0) as three_star_count " +
            "FROM puzzles p " +
            "LEFT JOIN puzzle_stats s ON p.id = s.puzzle_id " +
            "WHERE p.id = #{id} AND p.is_active = TRUE")
    Puzzle findById(@Param("id") Long id);

    // 根据难度查询残局列表
    @Select("SELECT p.*, " +
            "COALESCE(s.total_attempts, 0) as total_attempts, " +
            "COALESCE(s.total_completions, 0) as total_completions, " +
            "COALESCE(s.completion_rate, 0) as completion_rate, " +
            "COALESCE(s.three_star_count, 0) as three_star_count " +
            "FROM puzzles p " +
            "LEFT JOIN puzzle_stats s ON p.id = s.puzzle_id " +
            "WHERE p.difficulty = #{difficulty} AND p.is_active = TRUE " +
            "ORDER BY p.level_order")
    List<Puzzle> findByDifficulty(@Param("difficulty") String difficulty);

    // 查询所有活跃残局
    @Select("SELECT p.*, " +
            "COALESCE(s.total_attempts, 0) as total_attempts, " +
            "COALESCE(s.total_completions, 0) as total_completions, " +
            "COALESCE(s.completion_rate, 0) as completion_rate, " +
            "COALESCE(s.three_star_count, 0) as three_star_count " +
            "FROM puzzles p " +
            "LEFT JOIN puzzle_stats s ON p.id = s.puzzle_id " +
            "WHERE p.is_active = TRUE " +
            "ORDER BY p.difficulty, p.level_order")
    List<Puzzle> findAllActive();

    // 根据类型查询残局
    @Select("SELECT p.*, " +
            "COALESCE(s.total_attempts, 0) as total_attempts, " +
            "COALESCE(s.total_completions, 0) as total_completions, " +
            "COALESCE(s.completion_rate, 0) as completion_rate, " +
            "COALESCE(s.three_star_count, 0) as three_star_count " +
            "FROM puzzles p " +
            "LEFT JOIN puzzle_stats s ON p.id = s.puzzle_id " +
            "WHERE p.puzzle_type = #{puzzleType} AND p.is_active = TRUE " +
            "ORDER BY p.level_order")
    List<Puzzle> findByType(@Param("puzzleType") String puzzleType);

    // 插入残局
    @Insert("INSERT INTO puzzles (title, description, difficulty, puzzle_type, board_state, " +
            "first_player, player_color, win_condition, max_moves, optimal_moves, " +
            "solution, alternative_solutions, hint, hint_moves, level_order, is_active) " +
            "VALUES (#{title}, #{description}, #{difficulty}, #{puzzleType}, #{boardState}, " +
            "#{firstPlayer}, #{playerColor}, #{winCondition}, #{maxMoves}, #{optimalMoves}, " +
            "#{solution}, #{alternativeSolutions}, #{hint}, #{hintMoves}, #{levelOrder}, #{isActive})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Puzzle puzzle);

    // 更新残局
    @Update("UPDATE puzzles SET title = #{title}, description = #{description}, " +
            "difficulty = #{difficulty}, puzzle_type = #{puzzleType}, board_state = #{boardState}, " +
            "first_player = #{firstPlayer}, player_color = #{playerColor}, " +
            "win_condition = #{winCondition}, max_moves = #{maxMoves}, optimal_moves = #{optimalMoves}, " +
            "solution = #{solution}, alternative_solutions = #{alternativeSolutions}, " +
            "hint = #{hint}, hint_moves = #{hintMoves}, level_order = #{levelOrder}, is_active = #{isActive} " +
            "WHERE id = #{id}")
    int update(Puzzle puzzle);

    // 删除残局
    @Delete("DELETE FROM puzzles WHERE id = #{id}")
    int delete(@Param("id") Long id);

    // 统计各难度残局数量
    @Select("SELECT difficulty, COUNT(*) as count FROM puzzles WHERE is_active = TRUE GROUP BY difficulty")
    List<java.util.Map<String, Object>> countByDifficulty();
}
