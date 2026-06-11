# M1 基础设施 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\` 创建独立 Maven 项目，跑通 Spring Boot 4 + sa-token 1.45.0 + JPA + H2 启动流程；启动后用 `admin/admin` 登录拿 token，`/api/auth/me` 返回当前用户。

**Architecture:** 单 Maven module 项目，独立于 `agentscope-java` mono-repo。Web 层 Spring Boot 4 WebFlux；鉴权 `sa-token-reactor-spring-boot4-starter:1.45.0` 全局拦截器；持久化 Spring Data JPA + Hibernate 7；默认 H2 file 数据库；`sys_user` 表 + 启动种子 `admin/admin`。

**Tech Stack:**
- JDK 21
- Maven 3.8+
- Spring Boot 4.0.2 + WebFlux + Spring Framework 7（Jakarta EE 11）
- sa-token-reactor-spring-boot4-starter 1.45.0
- Spring Data JPA + Hibernate 7
- H2 2.3.232（默认 file 模式）
- BCrypt（密码加密，用 Spring Security crypto，仅 jar，不启用 Security 框架）
- JUnit 5 + StepVerifier
- 本地 Maven 仓库：`D:\PROGRAM\maven\Repository`

---

## File Structure

新建项目根目录：`D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\`

```
agentscope-builder-saton/
├── pom.xml                                                      # Maven 配置
├── .gitignore                                                   # 忽略 target/、.idea/、*.iml、logs/
├── README.md                                                    # 启动说明
├── src/main/java/io/agentscope/builder/saton/
│   ├── BuilderApp.java                                          # @SpringBootApplication 启动入口
│   ├── auth/
│   │   ├── SaTokenConfig.java                                   # SaReactorFilter 全局拦截
│   │   ├── AuthController.java                                  # POST /api/auth/login + GET /api/auth/me
│   │   ├── UserService.java                                     # 业务方法：登录校验 / 当前用户
│   │   ├── SysUserEntity.java                                   # JPA 实体
│   │   ├── SysUserRepository.java                               # JPA Repository
│   │   ├── SysUserSeeder.java                                   # @PostConstruct 种 admin/admin
│   │   ├── PasswordEncoderHolder.java                           # 单例 BCrypt
│   │   ├── dto/
│   │   │   ├── LoginRequest.java                                # record
│   │   │   ├── LoginResponse.java                               # record { token, userId, username }
│   │   │   └── MeResponse.java                                  # record { userId, username }
│   │   └── ex/
│   │       ├── BadCredentialsException.java                     # RuntimeException
│   │       └── AuthExceptionHandler.java                        # @RestControllerAdvice 转 401
│   └── common/
│       └── ApiError.java                                        # 统一错误响应 record
├── src/main/resources/
│   ├── application.yml                                          # 默认 H2 + sa-token 基本配置
│   └── logback-spring.xml                                       # 简单控制台日志
└── src/test/java/io/agentscope/builder/saton/
    └── auth/
        └── AuthFlowTest.java                                    # 集成测试：登录 → /me
```

**职责说明**：
- `BuilderApp` —— 启动入口，只放 `main()` 与 `@SpringBootApplication`，零业务
- `auth/SaTokenConfig` —— sa-token 全局拦截器配置（除 login 外都校验）
- `auth/AuthController` —— 两个端点，薄薄一层；业务委托 `UserService`
- `auth/UserService` —— `login()` / `currentUser()` 两个方法，里面调 sa-token
- `auth/SysUserEntity + Repository` —— 表 `sys_user`，主键 user_id (VARCHAR)
- `auth/SysUserSeeder` —— 启动时若表空则插 admin/admin
- `auth/PasswordEncoderHolder` —— 包装一个 BCryptPasswordEncoder 单例（不引入 Spring Security 框架，只用 crypto jar）
- `auth/ex/` —— 异常 + 全局处理
- `common/ApiError` —— 统一错误 JSON 结构 `{code, message}`

---

## Task 1: 创建项目根目录与 pom.xml

**Files:**
- Create: `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\pom.xml`
- Create: `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\.gitignore`
- Create: `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\README.md`

- [ ] **Step 1: 建项目根目录并初始化 git**

```powershell
New-Item -ItemType Directory -Force "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
git init
```

Expected: 输出 `Initialized empty Git repository in ...\.git\`

- [ ] **Step 2: 写 .gitignore**

写入 `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\.gitignore`：

```gitignore
# Build
target/
*.class

# IDE
.idea/
*.iml
*.iws
.vscode/
.project
.classpath
.settings/

# OS
Thumbs.db
.DS_Store

# Logs
logs/
*.log

# Local data (H2 file db, workspace)
data/
.agentscope/

# Env
.env
.env.local
```

- [ ] **Step 3: 写 pom.xml**

写入 `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.agentscope.builder</groupId>
    <artifactId>agentscope-builder-saton</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>AgentScope Builder (sa-token edition)</name>
    <description>Reactive multi-agent builder backend with sa-token auth</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>

        <spring.boot.version>4.0.2</spring.boot.version>
        <sa.token.version>1.45.0</sa.token.version>
        <spring.security.crypto.version>6.4.1</spring.security.crypto.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Web 层 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- sa-token reactor for SB4 -->
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-reactor-spring-boot4-starter</artifactId>
            <version>${sa.token.version}</version>
        </dependency>

        <!-- JPA + H2 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- BCrypt（仅 crypto jar，不引入 Security 框架） -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
            <version>${spring.security.crypto.version}</version>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring.boot.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 写 README.md**

写入 `D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\README.md`：

````markdown
# AgentScope Builder (sa-token edition)

原 `agentscope-builder` 的重做版本：
- Spring Boot 4 + WebFlux
- sa-token 鉴权
- 砍掉所有 IM 渠道
- 5 大工厂（Agent/Model/Tool/Skill/Hook）

设计文档见 `agentscope-java/docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`。

## 本地启动

```bash
mvn spring-boot:run
```

服务监听 8080。默认账号 `admin/admin`，首次登录后请改密。

## 登录示例

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# {"token":"...","userId":"admin","username":"admin"}

curl http://localhost:8080/api/auth/me \
  -H "satoken: <上一步拿到的 token>"
# {"userId":"admin","username":"admin"}
```

## Maven 本地仓库

由于使用了 `D:\PROGRAM\maven\Repository` 作为本地仓库，请确保 `~/.m2/settings.xml` 已配好 `localRepository` 节。

## 数据库

默认 H2 file 模式，数据落在 `./data/builderdb.mv.db`。要切 MySQL/PG，下个里程碑会加 profile `jdbc`。
````

- [ ] **Step 5: 验证 pom 可解析**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
mvn -q help:effective-pom -Doutput=target\effective-pom.xml
```

Expected: 退出码 0，`target/effective-pom.xml` 已生成且能找到 `sa-token-reactor-spring-boot4-starter`。若失败请检查本地 Maven 仓库配置。

- [ ] **Step 6: 提交**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
git add .gitignore pom.xml README.md
git commit -m "chore: init agentscope-builder-saton project skeleton"
```

---

## Task 2: 启动入口 BuilderApp + application.yml + logback

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/BuilderApp.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/logback-spring.xml`

- [ ] **Step 1: 写 BuilderApp**

写入 `src/main/java/io/agentscope/builder/saton/BuilderApp.java`：

```java
package io.agentscope.builder.saton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BuilderApp {

    public static void main(String[] args) {
        SpringApplication.run(BuilderApp.class, args);
    }
}
```

- [ ] **Step 2: 写 application.yml**

写入 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  application:
    name: agentscope-builder-saton
  datasource:
    url: jdbc:h2:file:./data/builderdb;DB_CLOSE_DELAY=-1;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
    show-sql: false
  h2:
    console:
      enabled: false

sa-token:
  token-name: satoken
  timeout: 604800            # 7 天
  active-timeout: -1
  is-concurrent: true
  is-share: true
  token-style: random-32
  is-log: false
```

- [ ] **Step 3: 写 logback-spring.xml**

写入 `src/main/resources/logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>

    <logger name="org.springframework" level="INFO"/>
    <logger name="cn.dev33.satoken" level="INFO"/>
    <logger name="io.agentscope.builder.saton" level="DEBUG"/>
</configuration>
```

- [ ] **Step 4: 验证空壳能启动**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
mvn -q compile
mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

> 由于这是 background task，本步实际操作改为：开一个终端跑上面命令，看到 `Netty started on port 8080` 后 Ctrl+C。

Expected 控制台关键行：
- `Started BuilderApp in X.XXX seconds`
- `Netty started on port 8080`
- 不报 sa-token / JPA 异常

注意：JPA 因为没实体类，可能会有 "Not registering JPA EntityManagerFactory" 之类警告，可暂时忽略 —— Task 3 会加实体。

- [ ] **Step 5: 提交**

```powershell
git add src/ pom.xml
git commit -m "feat: bootable empty Spring Boot 4 + WebFlux app"
```

---

## Task 3: sys_user 表 + Entity + Repository + 密码编码器

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/auth/SysUserEntity.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/SysUserRepository.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/PasswordEncoderHolder.java`
- Create: `src/test/java/io/agentscope/builder/saton/auth/SysUserRepositoryTest.java`

- [ ] **Step 1: 先写失败测试**

写入 `src/test/java/io/agentscope/builder/saton/auth/SysUserRepositoryTest.java`：

```java
package io.agentscope.builder.saton.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
```

- [ ] **Step 2: 运行测试，预期编译失败**

```powershell
mvn -q test -Dtest=SysUserRepositoryTest
```

Expected: 编译错误 —— `SysUserEntity` / `SysUserRepository` 不存在。

- [ ] **Step 3: 写 SysUserEntity**

写入 `src/main/java/io/agentscope/builder/saton/auth/SysUserEntity.java`：

```java
package io.agentscope.builder.saton.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sys_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_user_username", columnNames = "username"),
        indexes = @Index(name = "ix_sys_user_username", columnList = "username")
)
public class SysUserEntity {

    @Id
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 128, nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 写 SysUserRepository**

写入 `src/main/java/io/agentscope/builder/saton/auth/SysUserRepository.java`：

```java
package io.agentscope.builder.saton.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUserEntity, String> {

    Optional<SysUserEntity> findByUsername(String username);
}
```

- [ ] **Step 5: 运行测试，预期通过**

```powershell
mvn -q test -Dtest=SysUserRepositoryTest
```

Expected: BUILD SUCCESS。`saveAndFindByUsername` 和 `countWhenEmpty` 两个测试都 PASS。若 `@DataJpaTest` 报缺 `EmbeddedDatabaseConnection` 错，确认 H2 在 pom 里 `scope=runtime`（不是 `test`）。

- [ ] **Step 6: 写 PasswordEncoderHolder**

写入 `src/main/java/io/agentscope/builder/saton/auth/PasswordEncoderHolder.java`：

```java
package io.agentscope.builder.saton.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderHolder {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

- [ ] **Step 7: 提交**

```powershell
git add src/
git commit -m "feat(auth): SysUserEntity + Repository + BCrypt encoder"
```

---

## Task 4: 启动种子账号 SysUserSeeder

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/auth/SysUserSeeder.java`
- Create: `src/test/java/io/agentscope/builder/saton/auth/SysUserSeederTest.java`

- [ ] **Step 1: 先写失败测试**

写入 `src/test/java/io/agentscope/builder/saton/auth/SysUserSeederTest.java`：

```java
package io.agentscope.builder.saton.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

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
```

- [ ] **Step 2: 运行测试，预期失败**

```powershell
mvn -q test -Dtest=SysUserSeederTest
```

Expected: 测试失败 `admin user not seeded` —— 因为还没写 Seeder。

- [ ] **Step 3: 写 SysUserSeeder**

写入 `src/main/java/io/agentscope/builder/saton/auth/SysUserSeeder.java`：

```java
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
```

- [ ] **Step 4: 运行测试，预期通过**

```powershell
mvn -q test -Dtest=SysUserSeederTest
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```powershell
git add src/
git commit -m "feat(auth): seed default admin/admin user on startup"
```

---

## Task 5: sa-token 全局拦截器 SaTokenConfig

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/auth/SaTokenConfig.java`

- [ ] **Step 1: 写 SaTokenConfig**

写入 `src/main/java/io/agentscope/builder/saton/auth/SaTokenConfig.java`：

```java
package io.agentscope.builder.saton.auth;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfig {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/auth/login",
                        "/actuator/health",
                        "/actuator/info",
                        "/favicon.ico"
                )
                .setAuth(obj -> SaRouter.match("/**").check(r -> StpUtil.checkLogin()));
    }
}
```

> 暂不加 `setError` —— sa-token 默认抛 `NotLoginException`，由后续 Task 6 的全局异常处理器统一转 401。

- [ ] **Step 2: 启动验证（手动 smoke test，本步可跳过）**

如果当前已开 `mvn spring-boot:run`，Ctrl+C 重启即可。

- [ ] **Step 3: 提交**

```powershell
git add src/
git commit -m "feat(auth): sa-token global reactor filter"
```

---

## Task 6: 统一错误响应 + 异常处理器

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/common/ApiError.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/ex/BadCredentialsException.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/ex/AuthExceptionHandler.java`

- [ ] **Step 1: 写 ApiError record**

写入 `src/main/java/io/agentscope/builder/saton/common/ApiError.java`：

```java
package io.agentscope.builder.saton.common;

public record ApiError(int code, String message) {

    public static ApiError of(int code, String message) {
        return new ApiError(code, message);
    }
}
```

- [ ] **Step 2: 写 BadCredentialsException**

写入 `src/main/java/io/agentscope/builder/saton/auth/ex/BadCredentialsException.java`：

```java
package io.agentscope.builder.saton.auth.ex;

public class BadCredentialsException extends RuntimeException {

    public BadCredentialsException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: 写 AuthExceptionHandler**

写入 `src/main/java/io/agentscope/builder/saton/auth/ex/AuthExceptionHandler.java`：

```java
package io.agentscope.builder.saton.auth.ex;

import cn.dev33.satoken.exception.NotLoginException;
import io.agentscope.builder.saton.common.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiError> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, "not logged in: " + e.getType()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(401, e.getMessage()));
    }
}
```

- [ ] **Step 4: 编译验证**

```powershell
mvn -q compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```powershell
git add src/
git commit -m "feat(auth): unified ApiError + auth exception handler"
```

---

## Task 7: UserService 登录业务

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/auth/dto/LoginRequest.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/dto/LoginResponse.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/dto/MeResponse.java`
- Create: `src/main/java/io/agentscope/builder/saton/auth/UserService.java`
- Create: `src/test/java/io/agentscope/builder/saton/auth/UserServiceTest.java`

- [ ] **Step 1: 写三个 DTO record**

写入 `src/main/java/io/agentscope/builder/saton/auth/dto/LoginRequest.java`：

```java
package io.agentscope.builder.saton.auth.dto;

public record LoginRequest(String username, String password) {}
```

写入 `src/main/java/io/agentscope/builder/saton/auth/dto/LoginResponse.java`：

```java
package io.agentscope.builder.saton.auth.dto;

public record LoginResponse(String token, String userId, String username) {}
```

写入 `src/main/java/io/agentscope/builder/saton/auth/dto/MeResponse.java`：

```java
package io.agentscope.builder.saton.auth.dto;

public record MeResponse(String userId, String username) {}
```

- [ ] **Step 2: 写失败测试**

写入 `src/test/java/io/agentscope/builder/saton/auth/UserServiceTest.java`：

```java
package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.ex.BadCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired UserService userService;

    @Test
    void loginWithCorrectAdminCreds() {
        LoginResponse resp = userService.login(new LoginRequest("admin", "admin"));
        assertNotNull(resp.token());
        assertEquals("admin", resp.userId());
        assertEquals("admin", resp.username());
    }

    @Test
    void loginWithWrongPasswordThrows() {
        assertThrows(BadCredentialsException.class,
                () -> userService.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void loginWithUnknownUserThrows() {
        assertThrows(BadCredentialsException.class,
                () -> userService.login(new LoginRequest("nobody", "x")));
    }
}
```

- [ ] **Step 3: 运行测试，预期编译失败**

```powershell
mvn -q test -Dtest=UserServiceTest
```

Expected: 编译错误 —— 没有 `UserService`。

- [ ] **Step 4: 写 UserService**

写入 `src/main/java/io/agentscope/builder/saton/auth/UserService.java`：

```java
package io.agentscope.builder.saton.auth;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import io.agentscope.builder.saton.auth.ex.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
```

- [ ] **Step 5: 运行测试，预期通过**

```powershell
mvn -q test -Dtest=UserServiceTest
```

Expected: BUILD SUCCESS，3 个测试全 PASS。

> 注意：`StpUtil.login()` 在测试环境（无 HTTP 上下文）能 work 是因为 sa-token 用 `SaStorage` 抽象，离开 HTTP 也能跑（落到 ThreadLocal）。

- [ ] **Step 6: 提交**

```powershell
git add src/
git commit -m "feat(auth): UserService login + currentUser"
```

---

## Task 8: AuthController 暴露 REST 端点

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/auth/AuthController.java`

- [ ] **Step 1: 写 AuthController**

写入 `src/main/java/io/agentscope/builder/saton/auth/AuthController.java`：

```java
package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> userService.login(req))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/me")
    public Mono<MeResponse> me() {
        return Mono.fromCallable(userService::currentUser)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
```

> 用 `fromCallable + boundedElastic` 把阻塞的 JPA 调用挪出 Netty 线程，这是 WebFlux + JPA 的标准做法。

- [ ] **Step 2: 编译验证**

```powershell
mvn -q compile
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```powershell
git add src/
git commit -m "feat(auth): AuthController /api/auth/login + /me"
```

---

## Task 9: 集成测试 —— 完整登录流程

**Files:**
- Create: `src/test/java/io/agentscope/builder/saton/auth/AuthFlowTest.java`

- [ ] **Step 1: 写集成测试**

写入 `src/test/java/io/agentscope/builder/saton/auth/AuthFlowTest.java`：

```java
package io.agentscope.builder.saton.auth;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.auth.dto.MeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

    @Autowired WebTestClient client;

    @Test
    void loginThenMe() {
        // 1. 登录拿 token
        LoginResponse login = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(login);
        assertNotNull(login.token());
        assertEquals("admin", login.userId());

        // 2. 用 token 调 /me
        MeResponse me = client.get()
                .uri("/api/auth/me")
                .header("satoken", login.token())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MeResponse.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(me);
        assertEquals("admin", me.userId());
        assertEquals("admin", me.username());
    }

    @Test
    void meWithoutTokenReturns401() {
        client.get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 2: 运行测试**

```powershell
mvn -q test -Dtest=AuthFlowTest
```

Expected: BUILD SUCCESS，3 个测试 PASS。

若 `WebTestClient` 没自动注入，需在测试类加 `@AutoConfigureWebTestClient`：

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
class AuthFlowTest { ... }
```

- [ ] **Step 3: 提交**

```powershell
git add src/
git commit -m "test(auth): end-to-end login → /me integration test"
```

---

## Task 10: 全量回归 + 手动 smoke + 收尾提交

**Files:** 无新文件

- [ ] **Step 1: 全量测试**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
mvn -q test
```

Expected: BUILD SUCCESS。所有测试 PASS（应该有 7-8 个测试方法跨 4 个测试类）。

- [ ] **Step 2: 手动 smoke test —— 启动并 curl 一遍**

打开两个 PowerShell 窗口。

窗口 A：

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
mvn spring-boot:run
```

等待看到 `Started BuilderApp` 与 `Seeded default user: admin / admin` 警告。

窗口 B：

```powershell
# 登录
$resp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
    -ContentType "application/json" `
    -Body '{"username":"admin","password":"admin"}'
$resp

# 取出 token
$token = $resp.token

# 调 /me
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/auth/me `
    -Headers @{ "satoken" = $token }
```

Expected window B 输出：

```
token       userId username
-----       ------ --------
<32 字符>   admin  admin

userId username
------ --------
admin  admin
```

不传 token 应返回 401：

```powershell
try { Invoke-RestMethod http://localhost:8080/api/auth/me } catch { $_.Exception.Response.StatusCode }
# Unauthorized
```

窗口 A 按 Ctrl+C 关闭服务。

- [ ] **Step 3: 检查 H2 数据落地**

```powershell
ls "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton\data\"
```

Expected: 看到 `builderdb.mv.db` 文件。

- [ ] **Step 4: 收尾提交 + tag**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-builder-saton"
git log --oneline | head -15
git tag m1-bootstrap-done
```

Expected: 共约 9 个 commit。

---

## Self-Review 检查表

- [x] **Spec coverage**：M1 仅覆盖 spec 第 9 节 M1 行的 4 个目标（pom、sa-token、JPA+H2、seed admin、login+me），不超前。
- [x] **Placeholder scan**：无 TBD/TODO；所有代码块完整。
- [x] **Type consistency**：`SysUserEntity` / `SysUserRepository` / `LoginRequest` / `LoginResponse` / `MeResponse` 在所有 task 中名字一致；`StpUtil.getLoginIdAsString()` / `StpUtil.login()` / `StpUtil.getTokenValue()` 是 sa-token 标准 API。
- [x] **Test coverage**：每个核心类有对应测试（Repo / Seeder / Service / 集成）。
- [x] **依赖完整性**：pom 包含的依赖正好够本里程碑用，无未来里程碑的依赖污染。
- [x] **平台一致性**：所有命令都是 PowerShell 语法（无 bash 残留）；路径用 Windows 反斜杠。

---

**M1 完成定义**：

1. `mvn test` 全部 PASS
2. `mvn spring-boot:run` 启动后能通过 `/api/auth/login` 拿到 token，`/api/auth/me` 返回 `admin`
3. 数据库文件 `data/builderdb.mv.db` 已生成
4. Tag `m1-bootstrap-done` 已打

M1 完成后，告知用户开始写 M2 计划（资源管理 + 加密 Converter）。
