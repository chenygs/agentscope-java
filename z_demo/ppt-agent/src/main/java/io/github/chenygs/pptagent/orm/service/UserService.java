package io.github.chenygs.pptagent.orm.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import io.github.chenygs.pptagent.orm.entity.User;
import io.github.chenygs.pptagent.orm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务 — 登录、用户信息、初始化
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 应用启动完毕后，确保默认 admin 账号（密码 123456）存在。
     * <p>用 ApplicationRunner 而非 @PostConstruct，确保 JPA / DataSource 已就绪。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = User.builder()
                        .username("admin")
                        .passwordHash(encoder.encode("123456"))
                        .nickname("管理员")
                        .enabled(true)
                        .build();
                userRepository.save(admin);
                log.info("✅ Default admin user created (username=admin, password=123456)");
            } else {
                log.info("Admin user already exists, skip init");
            }
        } catch (Exception e) {
            log.error("❌ Failed to init admin user: {}", e.getMessage(), e);
        }
    }

    /**
     * 用户登录 — 验证用户名密码，成功后调用 Sa-Token 签发 token
     */
    @Transactional
    public SaTokenInfo login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalStateException("账号已被禁用");
        }

        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("密码错误");
        }

        // Sa-Token 登录：以 userId 作为登录账号
        StpUtil.login(user.getId());

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return StpUtil.getTokenInfo();
    }

    /**
     * 获取当前登录用户的脱敏信息
     */
    public Map<String, Object> getCurrentUserInfo() {
        Long userId = Long.valueOf(StpUtil.getLoginIdAsString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getNickname());
        info.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
        info.put("roles", new String[]{"super"});
        info.put("homePath", "/ppt-agent");
        return info;
    }
}
