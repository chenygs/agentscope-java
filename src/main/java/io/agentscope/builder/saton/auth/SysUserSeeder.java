package io.agentscope.builder.saton.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SysUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(SysUserSeeder.class);

    private final SysUserRepository repo;
    private final PasswordEncoder encoder;

    public SysUserSeeder(SysUserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @PostConstruct
    public void seedDefaultAdmin() {
        if (repo.findByUsername("admin").isPresent()) {
            return;
        }
        SysUserEntity admin = new SysUserEntity();
        admin.setUserId("admin");
        admin.setUsername("admin");
        admin.setPasswordHash(encoder.encode("admin"));
        admin.setCreatedAt(System.currentTimeMillis());
        repo.save(admin);

        log.warn("============================================================");
        log.warn(" Seeded default user: admin / admin");
        log.warn(" PLEASE CHANGE THE PASSWORD AFTER FIRST LOGIN!");
        log.warn("============================================================");
    }
}
