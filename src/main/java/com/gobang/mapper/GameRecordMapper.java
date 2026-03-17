package com.gobang.mapper;

import com.gobang.model.entity.GameRecord;
import org.apache.ibatis.annotations.*;

/**
 * 对局记录Mapper
 */
@Mapper
public interface GameRecordMapper {

    /**
     * 插入对局记录
     */
    @Insert("INSERT INTO game_record (room_id, black_player_id, white_player_id, winner_id, win_color, " +
            "end_reason, move_count, duration, black_rating_before, black_rating_after, black_rating_change, " +
            "white_rating_before, white_rating_after, white_rating_change, board_state, moves) " +
            "VALUES (#{roomId}, #{blackPlayerId}, #{whitePlayerId}, #{winnerId}, #{winColor}, #{endReason}, " +
            "#{moveCount}, #{duration}, #{blackRatingBefore}, #{blackRatingAfter}, #{blackRatingChange}, " +
            "#{whiteRatingBefore}, #{whiteRatingAfter}, #{whiteRatingChange}, #{boardState}, #{moves})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GameRecord record);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM game_record WHERE id = #{id}")
    GameRecord findById(@Param("id") Long id);

    /**
     * 根据房间ID查询
     */
    @Select("SELECT * FROM game_record WHERE room_id = #{roomId}")
    GameRecord findByRoomId(@Param("roomId") String roomId);

    /**
     * 查询用户的对局记录
     */
    @Select("SELECT * FROM game_record WHERE black_player_id = #{userId} OR white_player_id = #{userId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<GameRecord> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 统计用户对局数
     */
    @Select("SELECT COUNT(*) FROM game_record WHERE black_player_id = #{userId} OR white_player_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
