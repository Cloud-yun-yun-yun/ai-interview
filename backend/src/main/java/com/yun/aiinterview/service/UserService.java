package com.yun.aiinterview.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yun.aiinterview.entity.User;
import com.yun.aiinterview.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {
}