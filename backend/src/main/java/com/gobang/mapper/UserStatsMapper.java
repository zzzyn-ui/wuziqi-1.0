package com.gobang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gobang.model.dto.UserStatsDTO;
import com.gobang.model.entity.UserStats;
import org.apache.ibatis.annotations.*;

import java.util.Map;

/**
 * 用户统计Mapper
 * 继承MyBatis-Plus的BaseMapper以获得默认CRUD方法
 */
@Mapper
public interface UserStatsMapper extends BaseMapper<UserStats> {

    /**
     * 根据用户ID查询 - 返回DTO避免实体映射问题
     */
    @Select("SELECT user_id as userId, total_games as totalGames, wins as wins, losses as losses, " +
            "draws as draws, max_rating as maxRating, current_streak as currentStreak, max_streak as maxStreak, " +
            "total_moves as totalMoves, avg_moves_per_game as avgMovesPerGame, fastest_win as fastestWin, " +
            "updated_at as updatedAt FROM user_stats WHERE user_id = #{userId}")
    UserStatsDTO findStatsDTOByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询 - 返回Map避免实体映射问题
     */
    @Select("SELECT user_id as userId, total_games as totalGames, wins as wins, losses as losses, " +
            "draws as draws, max_rating as maxRating, current_streak as currentStreak, max_streak as maxStreak, " +
            "total_moves as totalMoves, avg_moves_per_game as avgMovesPerGame, fastest_win as fastestWin, " +
            "updated_at as updatedAt FROM user_stats WHERE user_id = #{userId}")
    Map<String, Object> findStatsMapByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询
     */
    @Select("SELECT user_id, total_games, wins, losses, draws, max_rating, current_streak, max_streak, total_moves, avg_moves_per_game, fastest_win, updated_at FROM user_stats WHERE user_id = #{userId}")
    UserStats findByUserId(@Param("userId") Long userId);

    /**
     * 插入统计 - 避免与MyBatis-Plus默认方法冲突
     */
    @Insert("INSERT INTO user_stats (user_id, total_games, wins, losses, draws, max_rating, " +
            "current_streak, max_streak, total_moves, avg_moves_per_game) " +
            "VALUES (#{userId}, #{totalGames}, #{wins}, #{losses}, #{draws}, #{maxRating}, " +
            "#{currentStreak}, #{maxStreak}, #{totalMoves}, #{avgMovesPerGame})")
    int insertStats(UserStats stats);

    /**
     * 更新统计 - 避免与MyBatis-Plus默认方法冲突
     */
    @Update("UPDATE user_stats SET total_games = #{totalGames}, wins = #{wins}, losses = #{losses}, " +
            "draws = #{draws}, max_rating = #{maxRating}, current_streak = #{currentStreak}, " +
            "max_streak = #{maxStreak}, total_moves = #{totalMoves}, avg_moves_per_game = #{avgMovesPerGame}, " +
            "fastest_win = #{fastestWin} WHERE user_id = #{userId}")
    int updateStats(UserStats stats);

    /**
     * 更新胜负统计
     */
    @Update("UPDATE user_stats SET total_games = total_games + 1, " +
            "wins = wins + #{winAdd}, losses = losses + #{lossAdd}, draws = draws + #{drawAdd}, " +
            "total_moves = total_moves + #{moves}, " +
            "avg_moves_per_game = (total_moves + #{moves}) / (total_games + 1), " +
            "current_streak = #{streak}, max_streak = GREATEST(max_streak, #{streak}) " +
            "WHERE user_id = #{userId}")
    int updateGameStats(@Param("userId") Long userId, @Param("winAdd") int winAdd,
                        @Param("lossAdd") int lossAdd, @Param("drawAdd") int drawAdd,
                        @Param("moves") int moves, @Param("streak") int streak);
}
