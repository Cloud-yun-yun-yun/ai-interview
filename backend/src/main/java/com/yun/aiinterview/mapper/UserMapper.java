package com.yun.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.yun.aiinterview.entity.User;
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
