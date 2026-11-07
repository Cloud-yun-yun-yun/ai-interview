package com.yun.aiinterview.controller;

import cn.hutool.core.lang.UUID;
import lombok.RequiredArgsConstructor;
import com.yun.aiinterview.entity.User;
import com.yun.aiinterview.pojo.dto.LoginParamDTO;
import com.yun.aiinterview.result.Result;
import com.yun.aiinterview.service.InterviewService;
import com.yun.aiinterview.service.UserService;
import com.yun.aiinterview.pojo.vo.UserLoginVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final StringRedisTemplate redisTemplate;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final InterviewService interviewService;

    private static final String TOKEN_PREFIX = "login:token:";
    private static final long TTL_MINUTES = 10080; // 7天

    @PostMapping("/login")
    public Result<UserLoginVO> login(@RequestBody LoginParamDTO loginParamDTO){
        User user = userService.lambdaQuery()
                .eq(User::getUsername,loginParamDTO.username()).one();
        if(user == null){
            return Result.error("用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginParamDTO.password(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + token,
                String.valueOf(user.getId()),
                TTL_MINUTES, TimeUnit.MINUTES
        );
        String activeSessionId = interviewService.findActiveSessionId(user.getId());
        return Result.success(new UserLoginVO(user.getId(), token, activeSessionId));
    }
    @PostMapping("/register")
    public Result<String> register(@RequestBody LoginParamDTO dto){

        User user = User.builder()
                .username(dto.username())
                .password(passwordEncoder.encode(dto.password()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userService.save(user);
        return Result.success("注册成功");
    }
}