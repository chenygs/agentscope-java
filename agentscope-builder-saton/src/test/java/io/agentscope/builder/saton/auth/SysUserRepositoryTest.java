package io.agentscope.builder.saton.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class SysUserRepositoryTest {

    @Autowired SysUserRepository repo;

    @Test
    void saveAndFindByUsername() {
        SysUserEntity u = new SysUserEntity();
        u.setUserId("alice");
        u.setUsername("alice");
        u.setPasswordHash("$2a$10$dummyhashvalue");
        u.setCreatedAt(System.currentTimeMillis());
        repo.save(u);

        Optional<SysUserEntity> found = repo.findByUsername("alice");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUserId());
    }

    @Test
    void countWhenEmpty() {
        assertEquals(0, repo.count());
    }
}
