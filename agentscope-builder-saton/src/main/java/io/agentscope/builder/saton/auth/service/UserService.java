package io.agentscope.builder.saton.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.auth.orm.dto.LoginRequest;
import io.agentscope.builder.saton.auth.orm.dto.LoginResponse;
import io.agentscope.builder.saton.auth.orm.dto.MeResponse;
import io.agentscope.builder.saton.auth.service.ex.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import io.agentscope.builder.saton.auth.orm.entity.SysUserEntity;
import io.agentscope.builder.saton.auth.orm.repository.SysUserRepository;

@Service
public class UserService {

    private final SysUserRepository repo;
    private final PasswordEncoder encoder;

    public UserService(SysUserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public LoginResponse login(LoginRequest req) {
        SysUserEntity user = repo.findByUsername(req.username())
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }
        StpUtil.login(user.getUserId());
        String token = StpUtil.getTokenValue();
        return new LoginResponse(token, user.getUserId(), user.getUsername());
    }

    public MeResponse currentUser() {
        String userId = StpUtil.getLoginIdAsString();
        SysUserEntity user = repo.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("user not found"));
        return new MeResponse(user.getUserId(), user.getUsername());
    }
}
