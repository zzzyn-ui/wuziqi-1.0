package com.gobang.service;

import com.gobang.mapper.GameFavoriteMapper;
import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.GameFavorite;
import com.gobang.model.entity.GameRecord;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 对局收藏服务
 */
public class GameFavoriteService {

    private static final Logger logger = LoggerFactory.getLogger(GameFavoriteService.class);

    private final SqlSessionFactory sqlSessionFactory;

    public GameFavoriteService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 添加收藏
     */
    public boolean addFavorite(Long userId, Long gameRecordId, String note, String tags) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);

            // 检查是否已收藏
            GameFavorite existing = mapper.findByUserAndRecord(userId, gameRecordId);
            if (existing != null) {
                logger.warn("用户已收藏该对局: userId={}, gameRecordId={}", userId, gameRecordId);
                return false;
            }

            // 验证对局记录是否存在
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            GameRecord record = recordMapper.findById(gameRecordId);
            if (record == null) {
                logger.warn("对局记录不存在: gameRecordId={}", gameRecordId);
                return false;
            }

            GameFavorite favorite = new GameFavorite(userId, gameRecordId);
            favorite.setNote(note);
            favorite.setTags(tags);

            mapper.insert(favorite);
            session.commit();
            logger.info("添加收藏: userId={}, gameRecordId={}", userId, gameRecordId);
            return true;
        }
    }

    /**
     * 取消收藏
     */
    public boolean removeFavorite(Long userId, Long gameRecordId) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            int deleted = mapper.delete(userId, gameRecordId);
            if (deleted > 0) {
                logger.info("取消收藏: userId={}, gameRecordId={}", userId, gameRecordId);
                return true;
            }
            return false;
        }
    }

    /**
     * 获取用户收藏列表
     */
    public List<GameFavorite> getUserFavorites(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            return mapper.findByUserId(userId);
        }
    }

    /**
     * 检查是否已收藏
     */
    public boolean isFavorited(Long userId, Long gameRecordId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            return mapper.findByUserAndRecord(userId, gameRecordId) != null;
        }
    }

    /**
     * 更新收藏备注
     */
    public boolean updateFavorite(Long userId, Long gameRecordId, String note, String tags, Boolean isPublic) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            GameFavorite favorite = mapper.findByUserAndRecord(userId, gameRecordId);

            if (favorite == null) {
                return false;
            }

            favorite.setNote(note);
            favorite.setTags(tags);
            if (isPublic != null) {
                favorite.setIsPublic(isPublic);
            }

            mapper.update(favorite);
            session.commit();
            logger.info("更新收藏备注: userId={}, gameRecordId={}", userId, gameRecordId);
            return true;
        }
    }

    /**
     * 获取收藏数量
     */
    public int getFavoriteCount(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            return mapper.countByUserId(userId);
        }
    }

    /**
     * 获取公开收藏
     */
    public List<GameFavorite> getPublicFavorites(int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameFavoriteMapper mapper = session.getMapper(GameFavoriteMapper.class);
            return mapper.findPublicFavorites(limit);
        }
    }
}
