package com.gobang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gobang.model.entity.GameRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对局记录 Mapper
 * 继承 MyBatis-Plus BaseMapper，自动拥有 CRUD 方法
 */
@Mapper
public interface GameRecordMapper extends BaseMapper<GameRecord> {
    // MyBatis-Plus 自动提供 CRUD 方法

    // 如需自定义 SQL，可以在这里添加方法并创建对应的 XML 文件
}
