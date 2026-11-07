package com.yun.aiinterview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_name")
    private String username;

    @TableField("password")
    private String password;
    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;
}
