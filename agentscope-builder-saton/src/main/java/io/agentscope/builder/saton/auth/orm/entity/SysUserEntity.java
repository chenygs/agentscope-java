package io.agentscope.builder.saton.auth.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class SysUserEntity {

    @Id
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "username", length = 64, nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", length = 128, nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private long createdAt;
}
