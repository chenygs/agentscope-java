package io.agentscope.builder.saton.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;
import io.agentscope.builder.saton.auth.orm.entity.SysUserEntity;
import io.agentscope.builder.saton.auth.orm.repository.SysUserRepository;

@SpringBootTest
class SysUserSeederTest {

    @Autowired SysUserRepository repo;

    @Test
    void adminSeedExistsAfterBoot() {
        SysUserEntity admin = repo.findByUsername("admin")
                .orElseThrow(() -> new AssertionError("admin user not seeded"));
        assertEquals("admin", admin.getUserId());
        assertNotNull(admin.getPasswordHash());
        assertTrue(admin.getPasswordHash().startsWith("$2"), "password should be bcrypt");
    }
}
