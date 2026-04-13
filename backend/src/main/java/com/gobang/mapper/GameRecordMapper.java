package com.gobang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gobang.model.entity.GameRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 对局记录Mapper
 * 使用 MyBatis-Plus BaseMapper 提供基础CRUD操作
 */
@Mapper
public interface GameRecordMapper extends BaseMapper<GameRecord> {

    /**
     * 根据房间ID查询对局记录
     */
    @Select("SELECT * FROM game_record WHERE room_id = #{roomId}")
    GameRecord findByRoomId(@Param("roomId") String roomId);

    /**
     * 查询用户的对局记录（所有）
     * 返回用户参与的所有对局，按时间倒序
     */
    @Select("SELECT * FROM game_record WHERE black_player_id = #{userId} OR white_player_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<GameRecord> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 查询用户的对局记录（最近三天）
     */
    @Select("SELECT * FROM game_record WHERE (black_player_id = #{userId} OR white_player_id = #{userId}) " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL 3 DAY) " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<GameRecord> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 统计用户对局总数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE black_player_id = #{userId} OR white_player_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计今日对局数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE DATE(CONVERT_TZ(created_at, '+00:00', @@session.time_zone)) = CURDATE()")
    int countToday();

    /**
     * 统计指定日期后的对局数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE created_at >= #{since}")
    int countSince(@Param("since") java.time.LocalDateTime since);

    /**
     * 获取最近的游戏记录
     */
    @Select("SELECT * FROM game_record ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<GameRecord> findRecent(@Param("limit") int limit);

    /**
     * 统计今日指定模式的对局数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE game_mode = #{gameMode} AND created_at >= #{since}")
    int countByModeSince(@Param("gameMode") String gameMode, @Param("since") java.time.LocalDateTime since);

    /**
     * 统计用户今日指定模式的对局数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE (black_player_id = #{userId} OR white_player_id = #{userId}) " +
            "AND game_mode = #{gameMode} AND created_at >= #{since}")
    int countByUserAndModeSince(@Param("userId") Long userId, @Param("gameMode") String gameMode,
                                @Param("since") java.time.LocalDateTime since);

    /**
     * 统计今日指定模式的胜场数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE game_mode = #{gameMode} AND winner_id = #{userId} " +
            "AND created_at >= #{since}")
    int countWinsByModeSince(@Param("gameMode") String gameMode, @Param("userId") Long userId,
                            @Param("since") java.time.LocalDateTime since);
}
