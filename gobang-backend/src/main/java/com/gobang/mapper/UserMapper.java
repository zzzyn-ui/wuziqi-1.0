package com.gobang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gobang.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 * 继承 MyBatis-Plus BaseMapper，自动拥有 CRUD 方法
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // MyBatis-Plus 自动提供：
    // - int insert(User entity)
    // - int deleteById(Serializable id)
    // - int updateById(User entity)
    // - User selectById(Serializable id)
    // - List<User> selectList(Wrapper<User> queryWrapper)
    // - Long selectCount(Wrapper<User> queryWrapper)
    // 等等...

    // 如需自定义 SQL，可以在这里添加方法并创建对应的 XML 文件
}
