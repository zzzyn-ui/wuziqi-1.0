package com.gobang.service;

import com.gobang.mapper.GameRecordMapper;
import com.gobang.model.entity.GameRecord;
import com.gobang.protocol.protobuf.GobangProto;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 对局记录服务
 */
public class RecordService {

    private static final Logger logger = LoggerFactory.getLogger(RecordService.class);
    private static final Gson gson = new Gson();

    private final SqlSessionFactory sqlSessionFactory;

    public RecordService(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /**
     * 保存对局记录
     */
    public void saveRecord(GameRecord record) {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            recordMapper.insert(record);
            logger.info("Saved game record for room: {}", record.getRoomId());
        }
    }

    /**
     * 获取用户的对局记录
     */
    public List<GameRecord> getUserRecords(Long userId, int limit) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.findByUserId(userId, limit);
        }
    }

    /**
     * 根据房间ID获取记录
     */
    public GameRecord getRecordByRoomId(String roomId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.findByRoomId(roomId);
        }
    }

    /**
     * 统计用户对局数
     */
    public int countUserGames(Long userId) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            GameRecordMapper recordMapper = session.getMapper(GameRecordMapper.class);
            return recordMapper.countByUserId(userId);
        }
    }

    /**
     * 获取回放数据
     */
    public GobangProto.ReplayData getReplayData(String roomId) {
        GameRecord record = getRecordByRoomId(roomId);
        if (record == null) {
            return null;
        }

        // 解析落子记录
        Type moveListType = new TypeToken<List<int[]>>() {}.getType();
        List<int[]> moves = gson.fromJson(record.getMoves(), moveListType);

        // 构建回放数据
        GobangProto.ReplayData.Builder builder = GobangProto.ReplayData.newBuilder()
                .setRoomId(record.getRoomId())
                .setWinnerId(record.getWinnerId() != null ? String.valueOf(record.getWinnerId()) : "")
                .setEndReason(record.getEndReason())
                .setCreatedAt(record.getCreatedAt() != null
                        ? record.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : System.currentTimeMillis());

        // 添加落子记录
        for (int[] move : moves) {
            GobangProto.MoveInfo moveInfo = GobangProto.MoveInfo.newBuilder()
                    .setX(move[0])
                    .setY(move[1])
                    .setColor(move[2])
                    .build();
            builder.addMoves(moveInfo);
        }

        return builder.build();
    }

    /**
     * 获取回放数据包
     */
    public GobangProto.Packet getReplayPacket(String roomId) {
        GobangProto.ReplayData replayData = getReplayData(roomId);
        if (replayData == null) {
            return null;
        }

        return GobangProto.Packet.newBuilder()
                .setType(GobangProto.MessageType.GAME_REPLAY_DATA)
                .setTimestamp(System.currentTimeMillis())
                .setBody(replayData.toByteString())
                .build();
    }
}
