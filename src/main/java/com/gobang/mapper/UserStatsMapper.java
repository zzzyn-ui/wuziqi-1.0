package com.gobang.mapper;

import com.gobang.model.entity.UserStats;
import org.apache.ibatis.annotations.*;

/**
 * 用户统计Mapper
 */
@Mapper
public interface UserStatsMapper {

    /**
     * 根据用户ID查询
     */
    @Select("SELECT * FROM user_stats WHERE user_id = #{userId}")
    UserStats findByUserId(@Param("userId") Long userId);

    /**
     * 插入统计
     */
    @Insert("INSERT INTO user_stats (user_id, total_games, wins, losses, draws, max_rating, " +
            "current_streak, max_streak, total_moves, avg_moves_per_game) " +
            "VALUES (#{userId}, #{totalGames}, #{wins}, #{losses}, #{draws}, #{maxRating}, " +
            "#{currentStreak}, #{maxStreak}, #{totalMoves}, #{avgMovesPerGame})")
    int insert(UserStats stats);

    /**
     * 更新统计
     */
    @Update("UPDATE user_stats SET total_games = #{totalGames}, wins = #{wins}, losses = #{losses}, " +
            "draws = #{draws}, max_rating = #{maxRating}, current_streak = #{currentStreak}, " +
            "max_streak = #{maxStreak}, total_moves = #{totalMoves}, avg_moves_per_game = #{avgMovesPerGame}, " +
            "fastest_win = #{fastestWin} WHERE user_id = #{userId}")
    int update(UserStats stats);

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
