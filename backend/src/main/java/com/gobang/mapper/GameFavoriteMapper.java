package com.gobang.mapper;

import com.gobang.model.entity.GameFavorite;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 对局收藏Mapper
 */
@Mapper
public interface GameFavoriteMapper {

    /**
     * 插入收藏
     */
    @Insert("INSERT INTO game_favorite (user_id, game_record_id, note, tags, is_public) " +
            "VALUES (#{userId}, #{gameRecordId}, #{note}, #{tags}, #{isPublic})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GameFavorite favorite);

    /**
     * 删除收藏
     */
    @Delete("DELETE FROM game_favorite WHERE user_id = #{userId} AND game_record_id = #{gameRecordId}")
    int delete(@Param("userId") Long userId, @Param("gameRecordId") Long gameRecordId);

    /**
     * 查询用户的所有收藏
     */
    @Select("SELECT * FROM game_favorite WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<GameFavorite> findByUserId(@Param("userId") Long userId);

    /**
     * 查询用户是否收藏了指定对局
     */
    @Select("SELECT * FROM game_favorite WHERE user_id = #{userId} AND game_record_id = #{gameRecordId}")
    GameFavorite findByUserAndRecord(@Param("userId") Long userId, @Param("gameRecordId") Long gameRecordId);

    /**
     * 查询公开的收藏
     */
    @Select("SELECT * FROM game_favorite WHERE is_public = 1 ORDER BY created_at DESC LIMIT #{limit}")
    List<GameFavorite> findPublicFavorites(@Param("limit") int limit);

    /**
     * 统计用户的收藏数量
     */
    @Select("SELECT COUNT(*) FROM game_favorite WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    /**
     * 更新收藏备注和标签
     */
    @Update("UPDATE game_favorite SET note = #{note}, tags = #{tags}, is_public = #{isPublic} " +
            "WHERE user_id = #{userId} AND game_record_id = #{gameRecordId}")
    int update(GameFavorite favorite);
}
