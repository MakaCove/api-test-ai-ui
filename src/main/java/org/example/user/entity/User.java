package org.example.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    /**
     * 角色：如 ADMIN / USER
     */
    private String role;

    /**
     * 是否启用
     */
    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

