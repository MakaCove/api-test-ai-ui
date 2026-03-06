package org.example.security.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.user.entity.User;
import org.example.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * 认证与注册：登录页、注册页、注册提交。
 * 登录校验由 Spring Security 完成，此处仅渲染页面与处理注册逻辑。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        log.debug("访问登录页");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        log.debug("访问注册页");
        return "auth/register";
    }

    /** 注册：校验后写入 t_user，成功后跳转登录页 */
    @PostMapping("/register")
    public String doRegister(@RequestParam("username") String username,
                             @RequestParam("password") String password,
                             @RequestParam("confirmPassword") String confirmPassword,
                             Model model) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            model.addAttribute("error", "用户名和密码不能为空");
            return "auth/register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的密码不一致");
            return "auth/register";
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User exists = userMapper.selectOne(wrapper);
        if (exists != null) {
            log.warn("注册失败：用户名已存在 username={}", username);
            model.addAttribute("error", "用户名已存在，请更换一个");
            return "auth/register";
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("用户注册成功 username={}", username);

        model.addAttribute("registerSuccess", true);
        return "auth/login";
    }
}
