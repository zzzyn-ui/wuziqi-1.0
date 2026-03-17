package com.gobang.service;

import com.gobang.mapper.UserSettingsMapper;
import com.gobang.model.entity.UserSettings;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户设置服务
 */
public class UserSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(UserSettingsService.class);

    private final SqlSessionFactory sqlSessionFactory;

    public UserSettingsService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 获取用户设置
     */
    public UserSettings getUserSettings(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserSettingsMapper mapper = session.getMapper(UserSettingsMapper.class);
            UserSettings settings = mapper.findByUserId(userId);

            // 如果没有设置，创建默认设置
            if (settings == null) {
                settings = new UserSettings(userId);
                mapper.insert(settings);
                session.commit();
                logger.info("创建默认用户设置: userId={}", userId);
            }

            return settings;
        }
    }

    /**
     * 更新用户设置
     */
    public void updateUserSettings(Long userId, Map<String, Object> updates) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserSettingsMapper mapper = session.getMapper(UserSettingsMapper.class);
            UserSettings settings = getUserSettings(userId);

            // 应用更新
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                switch (field) {
                    case "soundEnabled":
                        settings.setSoundEnabled((Boolean) value);
                        break;
                    case "musicEnabled":
                        settings.setMusicEnabled((Boolean) value);
                        break;
                    case "soundVolume":
                        settings.setSoundVolume((Integer) value);
                        break;
                    case "musicVolume":
                        settings.setMusicVolume((Integer) value);
                        break;
                    case "boardTheme":
                        settings.setBoardTheme((String) value);
                        break;
                    case "pieceStyle":
                        settings.setPieceStyle((String) value);
                        break;
                    case "autoMatch":
                        settings.setAutoMatch((Boolean) value);
                        break;
                    case "showRating":
                        settings.setShowRating((Boolean) value);
                        break;
                    case "language":
                        settings.setLanguage((String) value);
                        break;
                    case "timezone":
                        settings.setTimezone((String) value);
                        break;
                }
            }

            mapper.update(settings);
            session.commit();
            logger.info("更新用户设置: userId={}, updates={}", userId, updates);
        }
    }

    /**
     * 获取用户的单个设置值
     */
    public <T> T getSetting(Long userId, String key, Class<T> type) {
        UserSettings settings = getUserSettings(userId);

        switch (key) {
            case "soundEnabled": return type.cast(settings.getSoundEnabled());
            case "musicEnabled": return type.cast(settings.getMusicEnabled());
            case "soundVolume": return type.cast(settings.getSoundVolume());
            case "musicVolume": return type.cast(settings.getMusicVolume());
            case "boardTheme": return type.cast(settings.getBoardTheme());
            case "pieceStyle": return type.cast(settings.getPieceStyle());
            case "autoMatch": return type.cast(settings.getAutoMatch());
            case "showRating": return type.cast(settings.getShowRating());
            case "language": return type.cast(settings.getLanguage());
            case "timezone": return type.cast(settings.getTimezone());
            default: return null;
        }
    }

    /**
     * 获取用户的所有设置（用于前端）
     */
    public Map<String, Object> getSettingsAsMap(Long userId) {
        UserSettings settings = getUserSettings(userId);
        Map<String, Object> map = new HashMap<>();

        map.put("soundEnabled", settings.getSoundEnabled());
        map.put("musicEnabled", settings.getMusicEnabled());
        map.put("soundVolume", settings.getSoundVolume());
        map.put("musicVolume", settings.getMusicVolume());
        map.put("boardTheme", settings.getBoardTheme());
        map.put("pieceStyle", settings.getPieceStyle());
        map.put("autoMatch", settings.getAutoMatch());
        map.put("showRating", settings.getShowRating());
        map.put("language", settings.getLanguage());
        map.put("timezone", settings.getTimezone());

        return map;
    }

    /**
     * 重置用户设置为默认值
     */
    public void resetToDefault(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UserSettingsMapper mapper = session.getMapper(UserSettingsMapper.class);

            // 删除旧设置
            mapper.deleteByUserId(userId);

            // 创建新默认设置
            UserSettings settings = new UserSettings(userId);
            mapper.insert(settings);

            session.commit();
            logger.info("重置用户设置为默认值: userId={}", userId);
        }
    }
}
