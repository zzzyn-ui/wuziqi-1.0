package com.gobang.mapper;

import com.gobang.model.entity.UserSettings;
import org.apache.ibatis.annotations.*;

/**
 * 用户设置Mapper
 */
@Mapper
public interface UserSettingsMapper {

    /**
     * 根据用户ID查询设置
     */
    @Select("SELECT * FROM user_settings WHERE user_id = #{userId}")
    UserSettings findByUserId(@Param("userId") Long userId);

    /**
     * 插入用户设置
     */
    @Insert("INSERT INTO user_settings (user_id, sound_enabled, music_enabled, sound_volume, music_volume, " +
            "board_theme, piece_style, auto_match, show_rating, language, timezone) " +
            "VALUES (#{userId}, #{soundEnabled}, #{musicEnabled}, #{soundVolume}, #{musicVolume}, " +
            "#{boardTheme}, #{pieceStyle}, #{autoMatch}, #{showRating}, #{language}, #{timezone})")
    int insert(UserSettings settings);

    /**
     * 更新用户设置
     */
    @Update("UPDATE user_settings SET sound_enabled = #{soundEnabled}, music_enabled = #{musicEnabled}, " +
            "sound_volume = #{soundVolume}, music_volume = #{musicVolume}, board_theme = #{boardTheme}, " +
            "piece_style = #{pieceStyle}, auto_match = #{autoMatch}, show_rating = #{showRating}, " +
            "language = #{language}, timezone = #{timezone} WHERE user_id = #{userId}")
    int update(UserSettings settings);

    /**
     * 更新单个设置项
     */
    @Update("UPDATE user_settings SET #{fieldName} = #{value} WHERE user_id = #{userId}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    int updateSetting(@Param("userId") Long userId, @Param("fieldName") String fieldName, @Param("value") Object value);

    /**
     * 删除用户设置
     */
    @Delete("DELETE FROM user_settings WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
