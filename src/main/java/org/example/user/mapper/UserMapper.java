package org.example.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.user.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMapper extends BaseMapper<User> {
}

