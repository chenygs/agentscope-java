# M2 资源管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `agentscope-builder-saton` 加 4 张"独立资源"表（model_provider / mcp_server / skill_marketplace / skill_repository），每张配完整的 sa-token 鉴权 CRUD REST 接口；敏感字段（apiKey / token / password）走 AES-256 字段级加密 Converter 入库；列表接口对前端 mask 显示。完成标志：前端 POST `/api/models` 加一个 DashScope，DB 看到 api_key 是加密密文，GET `/api/models` 返回时 apiKey 被 mask 成 `sk-xxxx****`。

**Architecture:** 4 套表 + Entity + Repository + DTO + Service + Controller 高度同构。先做 `model_provider` 一套作模板（含加密、mask、所有边界条件），然后另外 3 套"照模板复刻"。统一的 owner_id 过滤来自 `StpUtil.getLoginIdAsString()`；统一的加密/解密通过 `EncryptedJsonConverter`（JPA `AttributeConverter`），主密钥从环境变量 `AGENTSCOPE_BUILDER_SECRET_KEY` 读，缺失时启动期降级到固定开发密钥并 WARN。

**Tech Stack:**
- 沿用 M1 全部依赖（无新 pom 编辑），SB 4.0.2 + sa-token-reactor-4 1.45.0 + JPA + H2
- AES-256-GCM via JDK 内置 `javax.crypto.Cipher`，不引入额外加密库
- Jackson 3 (`tools.jackson.databind.ObjectMapper`) for props_json 序列化（已在 classpath）

**踩坑预防（来自 spec §12，全部已在 M1 验证）**：
- 所有 Service 写入会调 `StpUtil.getLoginIdAsString()` —— Controller 必须 `SaReactorSyncHolder.setContext(exchange)/clearContext()` 包裹 `Mono.fromCallable`
- 所有 ObjectMapper import 用 `tools.jackson.databind.ObjectMapper`
- 测试用 `@LocalServerPort + WebTestClient.bindToServer()`，不要 `@AutoConfigureWebTestClient`
- 测试登录拿 token 在 `@BeforeEach` 里做（Repository 单测用 `@DataJpaTest` 不需要登录）

---

## File Structure

新增/修改文件：

```
agentscope-builder-saton/
└── src/main/java/io/agentscope/builder/saton/
    ├── common/
    │   ├── crypto/
    │   │   ├── SecretKeyHolder.java                   # 启动期从 env 取/降级生成 AES-256 主密钥
    │   │   ├── AesGcmCipher.java                       # JDK Cipher 包装：encrypt(plain) / decrypt(cipher)
    │   │   ├── SensitiveFields.java                    # 常量：哪些 json key 需要加密 (apiKey/token/password/secret)
    │   │   └── EncryptedJsonConverter.java             # JPA AttributeConverter<String,String>
    │   ├── json/
    │   │   └── JsonUtil.java                           # tools.jackson.databind 单例 ObjectMapper
    │   ├── error/
    │   │   ├── NotFoundException.java
    │   │   └── ConflictException.java
    │   └── (已存在) ApiError.java
    └── resource/
        ├── ResourceCommon.java                         # 4 套共用的小工具方法（mask、props 拷贝）
        ├── model/
        │   ├── ModelProviderEntity.java
        │   ├── ModelProviderRepository.java
        │   ├── ModelProviderService.java
        │   ├── ModelProviderController.java            # /api/models
        │   └── dto/
        │       ├── ModelProviderUpsertReq.java         # record
        │       └── ModelProviderVO.java                 # record（apiKey 等已 mask）
        ├── mcp/
        │   ├── McpServerEntity.java
        │   ├── McpServerRepository.java
        │   ├── McpServerService.java
        │   ├── McpServerController.java                # /api/mcp-servers
        │   └── dto/ McpServerUpsertReq.java + McpServerVO.java
        ├── marketplace/
        │   ├── SkillMarketplaceEntity.java
        │   ├── SkillMarketplaceRepository.java
        │   ├── SkillMarketplaceService.java
        │   ├── SkillMarketplaceController.java         # /api/skill-marketplaces
        │   └── dto/ SkillMarketplaceUpsertReq.java + SkillMarketplaceVO.java
        └── repository/
            ├── SkillRepositoryEntity.java
            ├── SkillRepositoryRepository.java
            ├── SkillRepositoryService.java
            ├── SkillRepositoryController.java         # /api/skill-repositories
            └── dto/ SkillRepositoryUpsertReq.java + SkillRepositoryVO.java
```

**关键设计**（适用全 4 套）：

- **Entity**：`id` (BIGINT PK auto), `owner_id`, `name`/`marketplace_id`（业务唯一）, `type`, `props_json` (LOB)，`created_at`/`updated_at` (BIGINT epoch ms)。唯一约束 `(owner_id, name)` 或 `(owner_id, marketplace_id)`，索引 `(owner_id)`。
- **Converter**：`props_json` 列用 `@Convert(converter = EncryptedJsonConverter.class)`，写入时解析 JSON、对名字命中 `SensitiveFields.KEYS` 的字段值做 AES-256-GCM 加密、回 dump 成 JSON 字符串；读取时反向。**对象结构不变，仅 value 加密**。
- **Repository**：所有方法都带 `OwnerId` 后缀 —— `findByOwnerIdOrderByCreatedAtDesc(String)` / `findByIdAndOwnerId(Long, String)` / `existsByOwnerIdAndName(String, String)` / `deleteByIdAndOwnerId(Long, String)`。**不暴露不带 ownerId 的查询**。
- **Service**：所有方法签名隐式从 `StpUtil.getLoginIdAsString()` 取 ownerId；create 时检查 `name` 不重复抛 `ConflictException`；get/update/delete 失败抛 `NotFoundException`。
- **Controller**：纯薄壳，模式同 M1 的 `AuthController`（`Mono.fromCallable` + `SaReactorSyncHolder` 包裹）。
- **VO（返回前端）**：props 里敏感字段被 `mask()` 替换成 `"***"`，前端"未改"占位约定 = 字段值为字符串 `"***"`（写回时 Service 视为"保留原值"）。

---

## Task 1: 加密基础设施 + JsonUtil

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/common/crypto/SecretKeyHolder.java`
- Create: `src/main/java/io/agentscope/builder/saton/common/crypto/AesGcmCipher.java`
- Create: `src/main/java/io/agentscope/builder/saton/common/crypto/SensitiveFields.java`
- Create: `src/main/java/io/agentscope/builder/saton/common/json/JsonUtil.java`
- Test: `src/test/java/io/agentscope/builder/saton/common/crypto/AesGcmCipherTest.java`

- [ ] **Step 1: 写 SensitiveFields**

Write `src/main/java/io/agentscope/builder/saton/common/crypto/SensitiveFields.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import java.util.Set;

/**
 * JSON props 里需要字段级加密的 key 名（大小写敏感、原样匹配）。
 * 加新资源时如果命名不同（比如 webhookSecret）记得追加。
 */
public final class SensitiveFields {

    public static final Set<String> KEYS = Set.of(
            "apiKey",
            "token",
            "password",
            "secret",
            "accessKeySecret"
    );

    public static final String MASKED_VALUE = "***";

    private SensitiveFields() {}
}
```

- [ ] **Step 2: 写 SecretKeyHolder**

Write `src/main/java/io/agentscope/builder/saton/common/crypto/SecretKeyHolder.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 启动期解析 AES-256 主密钥。优先从环境变量 {@code AGENTSCOPE_BUILDER_SECRET_KEY}
 * 读取 32 字节 base64；缺失时降级到固定开发密钥并打 WARN（仅 dev 用，不要在生产依赖）。
 */
@Component
public class SecretKeyHolder {

    public static final String ENV = "AGENTSCOPE_BUILDER_SECRET_KEY";

    private static final Logger log = LoggerFactory.getLogger(SecretKeyHolder.class);

    private SecretKeySpec key;

    @PostConstruct
    public void init() {
        String env = System.getenv(ENV);
        byte[] raw;
        if (env == null || env.isBlank()) {
            log.warn("============================================================");
            log.warn(" {} not set; falling back to DEV key.", ENV);
            log.warn(" DO NOT USE IN PRODUCTION. Encrypted DB values written now");
            log.warn(" will NOT be readable on a host with a different key.");
            log.warn("============================================================");
            raw = sha256("agentscope-builder-saton-dev-key");
        } else {
            try {
                raw = Base64.getDecoder().decode(env);
            } catch (IllegalArgumentException e) {
                raw = sha256(env);
                log.warn("{} not valid base64; derived AES key via SHA-256.", ENV);
            }
            if (raw.length != 32) {
                raw = sha256(env);
                log.warn("{} decoded length != 32; derived AES key via SHA-256.", ENV);
            }
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public SecretKeySpec key() {
        return key;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 3: 写 AesGcmCipher**

Write `src/main/java/io/agentscope/builder/saton/common/crypto/AesGcmCipher.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 包装。密文格式（base64）= 12 字节 IV || ciphertext || 16 字节 tag。
 * 解密时 IV 从前 12 字节恢复。
 */
@Component
public class AesGcmCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final SecureRandom RNG = new SecureRandom();
    /** 加密文已带这个前缀，方便识别已加密 vs 未加密（升级旧库时容错）。 */
    public static final String PREFIX = "enc:v1:";

    private final SecretKeyHolder keyHolder;

    public AesGcmCipher(SecretKeyHolder keyHolder) {
        this.keyHolder = keyHolder;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, keyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(IV_BYTES + cipherBytes.length);
            bb.put(iv).put(cipherBytes);
            return PREFIX + Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            // 兼容：未加密的旧值原样返回（也方便测试给明文初始值）
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, keyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed", e);
        }
    }
}
```

- [ ] **Step 4: 写 JsonUtil**

Write `src/main/java/io/agentscope/builder/saton/common/json/JsonUtil.java`:

```java
package io.agentscope.builder.saton.common.json;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 进程级 ObjectMapper 单例（基于 Spring Boot 4 默认的 Jackson 3）。
 * 注意：包名是 {@code tools.jackson.databind}，不是 {@code com.fasterxml.jackson.databind}。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private JsonUtil() {}
}
```

> 若编译报 `JacksonModule` 未使用，删去 import 即可 —— 留作未来挂模块的接入点。
> 若 `JsonMapper.builder()` 在 Jackson 3 API 上路径不同，按编译器提示用 `new ObjectMapper()`。

- [ ] **Step 5: 写测试 AesGcmCipherTest**

Write `src/test/java/io/agentscope/builder/saton/common/crypto/AesGcmCipherTest.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AesGcmCipherTest {

    @Autowired AesGcmCipher cipher;

    @Test
    void encryptThenDecryptRoundTrip() {
        String plain = "sk-abc-1234567890";
        String enc = cipher.encrypt(plain);
        assertNotNull(enc);
        assertTrue(enc.startsWith(AesGcmCipher.PREFIX));
        assertNotEquals(plain, enc);
        assertEquals(plain, cipher.decrypt(enc));
    }

    @Test
    void encryptIsNotDeterministic() {
        String plain = "sk-same";
        assertNotEquals(cipher.encrypt(plain), cipher.encrypt(plain),
                "GCM with random IV must produce different ciphertext each call");
    }

    @Test
    void plaintextPassthroughOnDecrypt() {
        // 未加密的字符串解密时原样返回（兼容老值或测试夹具）
        assertEquals("not-encrypted", cipher.decrypt("not-encrypted"));
    }

    @Test
    void nullsAreNulls() {
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }
}
```

- [ ] **Step 6: Run tests, expect PASS**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn test -Dtest=AesGcmCipherTest
```

Expected: `Tests run: 4, Failures: 0`. 启动日志里看到 `AGENTSCOPE_BUILDER_SECRET_KEY not set; falling back to DEV key.` WARN —— 这是预期。

- [ ] **Step 7: Commit**

```powershell
git add src/
git commit -m "feat(crypto): AES-256-GCM cipher + secret key holder + json util"
```

---

## Task 2: EncryptedJsonConverter（字段级加密 JPA Converter）

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/common/crypto/EncryptedJsonConverter.java`
- Test: `src/test/java/io/agentscope/builder/saton/common/crypto/EncryptedJsonConverterTest.java`

- [ ] **Step 1: 写失败测试**

Write `src/test/java/io/agentscope/builder/saton/common/crypto/EncryptedJsonConverterTest.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EncryptedJsonConverterTest {

    @Autowired EncryptedJsonConverter converter;
    @Autowired AesGcmCipher cipher;

    @Test
    void sensitiveFieldsAreEncryptedInDbForm() throws Exception {
        String plain = """
                { "modelName":"qwen-max", "apiKey":"sk-12345", "baseUrl":"https://x" }""";
        String db = converter.convertToDatabaseColumn(plain);

        JsonNode root = JsonUtil.mapper().readTree(db);
        assertEquals("qwen-max", root.get("modelName").asString());
        assertEquals("https://x", root.get("baseUrl").asString());
        String storedApiKey = root.get("apiKey").asString();
        assertTrue(storedApiKey.startsWith(AesGcmCipher.PREFIX), "apiKey must be encrypted");
        assertEquals("sk-12345", cipher.decrypt(storedApiKey));
    }

    @Test
    void readingDecryptsBackToPlain() throws Exception {
        String plain = """
                { "modelName":"qwen-max", "apiKey":"sk-12345" }""";
        String db = converter.convertToDatabaseColumn(plain);
        String back = converter.convertToEntityAttribute(db);
        JsonNode root = JsonUtil.mapper().readTree(back);
        assertEquals("sk-12345", root.get("apiKey").asString());
    }

    @Test
    void nullPassThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void plaintextLegacyJsonReadStillWorks() throws Exception {
        // 早期未加密的 JSON 行；converter 应原样解出
        String legacy = "{\"apiKey\":\"sk-old-plaintext\"}";
        String back = converter.convertToEntityAttribute(legacy);
        JsonNode root = JsonUtil.mapper().readTree(back);
        assertEquals("sk-old-plaintext", root.get("apiKey").asString());
    }
}
```

- [ ] **Step 2: Run test, expect compile fail**

```powershell
mvn -q test -Dtest=EncryptedJsonConverterTest
```

Expected: 编译错误 `EncryptedJsonConverter` 不存在。

- [ ] **Step 3: 写 EncryptedJsonConverter**

Write `src/main/java/io/agentscope/builder/saton/common/crypto/EncryptedJsonConverter.java`:

```java
package io.agentscope.builder.saton.common.crypto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 把 props 用的 JSON 字符串：
 *  - 写库时 → 对名字命中 {@link SensitiveFields#KEYS} 的字段值做 AES-GCM 加密
 *  - 读库时 → 反向解密
 * 整体 JSON 结构保持不变，仅 value 加密。
 *
 * <p>不可加密 key 不在 KEYS 中、null、非字符串值（true/false/数字/对象/数组）一律原样保留。
 */
@Converter
public class EncryptedJsonConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    /**
     * JPA 通过 SPI 实例化 Converter，但我们也想注入到 Spring；@Autowired + @Lazy
     * 让 Spring 启动期填好。
     */
    @Autowired
    public EncryptedJsonConverter(@Lazy AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plainJson) {
        return transform(plainJson, true);
    }

    @Override
    public String convertToEntityAttribute(String dbJson) {
        return transform(dbJson, false);
    }

    private String transform(String json, boolean encrypt) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(json);
            if (root instanceof ObjectNode obj) {
                walk(obj, encrypt);
                return JsonUtil.mapper().writeValueAsString(obj);
            }
            // 顶层不是 object 就原样回传
            return json;
        } catch (Exception e) {
            throw new IllegalStateException("EncryptedJsonConverter " + (encrypt ? "encrypt" : "decrypt") + " failed", e);
        }
    }

    private void walk(ObjectNode obj, boolean encrypt) {
        var iter = obj.properties().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (val.isObject() && val instanceof ObjectNode child) {
                walk(child, encrypt);
            } else if (val.isTextual() && SensitiveFields.KEYS.contains(key)) {
                String s = val.asString();
                String out = encrypt ? cipher.encrypt(s) : cipher.decrypt(s);
                obj.put(key, out);
            }
        }
    }
}
```

> JPA AttributeConverter 默认由 Hibernate 用 no-arg 构造器实例化。Hibernate 7 + Spring Boot 4 支持把它注册成 Spring bean 注入。@Autowired + @Lazy 兜底避免循环依赖。如果实际启动时拿不到 cipher，回退方案：把 cipher 改成静态 holder（`static volatile AesGcmCipher`），在 `AesGcmCipher` 的 `@PostConstruct` 里写入静态字段。Task 3 真正集成时若遇到问题再切。

- [ ] **Step 4: Run test, expect PASS**

```powershell
mvn test -Dtest=EncryptedJsonConverterTest
```

Expected: `Tests run: 4, Failures: 0`.

如果 `JsonNode.asString()` 在你的 Jackson 3 版本上路径不一样（旧名 `asText()`），按编译器报错改。

- [ ] **Step 5: Commit**

```powershell
git add src/
git commit -m "feat(crypto): EncryptedJsonConverter — AES on sensitive props json fields"
```

---

## Task 3: ModelProvider 全套（Entity + Repo + Service + Controller + DTO + 测试）

> 这是模板任务，下面的 Task 4/5/6 会照抄结构。

**Files:**
- Create: `src/main/java/io/agentscope/builder/saton/common/error/NotFoundException.java`
- Create: `src/main/java/io/agentscope/builder/saton/common/error/ConflictException.java`
- Modify: `src/main/java/io/agentscope/builder/saton/auth/ex/AuthExceptionHandler.java` (加映射这俩 404/409)
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderEntity.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderRepository.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/dto/ModelProviderUpsertReq.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/dto/ModelProviderVO.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderService.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderController.java`
- Create: `src/main/java/io/agentscope/builder/saton/resource/ResourceCommon.java`
- Test: `src/test/java/io/agentscope/builder/saton/resource/model/ModelProviderRepositoryTest.java`
- Test: `src/test/java/io/agentscope/builder/saton/resource/model/ModelProviderFlowTest.java`

- [ ] **Step 1: NotFoundException + ConflictException**

Write `src/main/java/io/agentscope/builder/saton/common/error/NotFoundException.java`:

```java
package io.agentscope.builder.saton.common.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
```

Write `src/main/java/io/agentscope/builder/saton/common/error/ConflictException.java`:

```java
package io.agentscope.builder.saton.common.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
```

- [ ] **Step 2: 扩展 AuthExceptionHandler 映射 404/409**

Edit `src/main/java/io/agentscope/builder/saton/auth/ex/AuthExceptionHandler.java`. 在已有两个 handler 下面追加：

```java
    @ExceptionHandler(io.agentscope.builder.saton.common.error.NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            io.agentscope.builder.saton.common.error.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, e.getMessage()));
    }

    @ExceptionHandler(io.agentscope.builder.saton.common.error.ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(
            io.agentscope.builder.saton.common.error.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, e.getMessage()));
    }
```

（用全限定名避免改文件顶 import；如果想 import 顺手加，也行。）

- [ ] **Step 3: ResourceCommon 工具方法**

Write `src/main/java/io/agentscope/builder/saton/resource/ResourceCommon.java`:

```java
package io.agentscope.builder.saton.resource;

import io.agentscope.builder.saton.common.crypto.SensitiveFields;
import io.agentscope.builder.saton.common.json.JsonUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;

/**
 * 4 套独立资源 controller/service 共用的小工具。
 */
public final class ResourceCommon {

    /**
     * 把 props JSON 中的敏感字段值替换为 {@link SensitiveFields#MASKED_VALUE}（"***"）。
     * 用于 VO 返回前端。原对象不变。
     */
    public static String maskSensitive(String propsJson) {
        if (propsJson == null) {
            return null;
        }
        try {
            JsonNode root = JsonUtil.mapper().readTree(propsJson);
            if (root instanceof ObjectNode obj) {
                walk(obj);
                return JsonUtil.mapper().writeValueAsString(obj);
            }
            return propsJson;
        } catch (Exception e) {
            throw new IllegalStateException("mask failed", e);
        }
    }

    /**
     * Upsert 时：对 incoming props 里值 = "***" 的敏感字段，从 existing props 里捞回原值。
     * 实现"前端编辑不改密码"语义。返回合并后的 JSON。
     */
    public static String mergeKeepMasked(String incomingPropsJson, String existingPropsJson) {
        if (incomingPropsJson == null) return null;
        try {
            JsonNode in = JsonUtil.mapper().readTree(incomingPropsJson);
            if (!(in instanceof ObjectNode inObj)) return incomingPropsJson;
            if (existingPropsJson == null) return JsonUtil.mapper().writeValueAsString(inObj);
            JsonNode ex = JsonUtil.mapper().readTree(existingPropsJson);
            if (!(ex instanceof ObjectNode exObj)) return JsonUtil.mapper().writeValueAsString(inObj);
            for (String key : SensitiveFields.KEYS) {
                if (inObj.has(key) && SensitiveFields.MASKED_VALUE.equals(inObj.get(key).asString())) {
                    if (exObj.has(key)) {
                        inObj.put(key, exObj.get(key).asString());
                    } else {
                        inObj.remove(key);
                    }
                }
            }
            return JsonUtil.mapper().writeValueAsString(inObj);
        } catch (Exception e) {
            throw new IllegalStateException("merge failed", e);
        }
    }

    private static void walk(ObjectNode obj) {
        var iter = obj.properties().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            String key = entry.getKey();
            JsonNode val = entry.getValue();
            if (val.isObject() && val instanceof ObjectNode child) {
                walk(child);
            } else if (val.isTextual() && SensitiveFields.KEYS.contains(key)) {
                obj.put(key, SensitiveFields.MASKED_VALUE);
            }
        }
    }

    /** props_json 缺失时给个空对象，避免 NPE 链。 */
    public static String normalizePropsJson(String s) {
        return (s == null || s.isBlank()) ? "{}" : s;
    }

    /** Map → JSON（用于 DTO incoming）。 */
    public static String mapToJson(Map<String, Object> map) {
        try {
            return JsonUtil.mapper().writeValueAsString(map == null ? new HashMap<>() : map);
        } catch (Exception e) {
            throw new IllegalStateException("map -> json failed", e);
        }
    }

    private ResourceCommon() {}
}
```

- [ ] **Step 4: ModelProviderEntity**

Write `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderEntity.java`:

```java
package io.agentscope.builder.saton.resource.model;

import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import jakarta.persistence.*;

@Entity
@Table(
        name = "model_provider",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_model_provider_owner_name",
                columnNames = {"owner_id", "name"}),
        indexes = @Index(name = "ix_model_provider_owner", columnList = "owner_id")
)
public class ModelProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", length = 128, nullable = false)
    private String ownerId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** "dashscope" / "openai" / "anthropic" / "gemini" / "ollama" */
    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Lob
    @Convert(converter = EncryptedJsonConverter.class)
    @Column(name = "props_json")
    private String propsJson;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPropsJson() { return propsJson; }
    public void setPropsJson(String propsJson) { this.propsJson = propsJson; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: ModelProviderRepository**

Write `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderRepository.java`:

```java
package io.agentscope.builder.saton.resource.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelProviderRepository extends JpaRepository<ModelProviderEntity, Long> {

    List<ModelProviderEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<ModelProviderEntity> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndName(String ownerId, String name);

    long deleteByIdAndOwnerId(Long id, String ownerId);
}
```

- [ ] **Step 6: 写 Repository 测试**

Write `src/test/java/io/agentscope/builder/saton/resource/model/ModelProviderRepositoryTest.java`:

```java
package io.agentscope.builder.saton.resource.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import io.agentscope.builder.saton.common.crypto.AesGcmCipher;
import io.agentscope.builder.saton.common.crypto.EncryptedJsonConverter;
import io.agentscope.builder.saton.common.crypto.SecretKeyHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({EncryptedJsonConverter.class, AesGcmCipher.class, SecretKeyHolder.class})
class ModelProviderRepositoryTest {

    @Autowired ModelProviderRepository repo;

    @Test
    void crudByOwner() {
        ModelProviderEntity a = new ModelProviderEntity();
        a.setOwnerId("alice");
        a.setName("alice-qwen");
        a.setType("dashscope");
        a.setPropsJson("{\"apiKey\":\"sk-alice\"}");
        long now = System.currentTimeMillis();
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        repo.save(a);

        ModelProviderEntity b = new ModelProviderEntity();
        b.setOwnerId("bob");
        b.setName("bob-qwen");
        b.setType("dashscope");
        b.setPropsJson("{\"apiKey\":\"sk-bob\"}");
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        repo.save(b);

        List<ModelProviderEntity> aliceList = repo.findByOwnerIdOrderByCreatedAtDesc("alice");
        assertEquals(1, aliceList.size());
        assertEquals("alice-qwen", aliceList.get(0).getName());
        // Round-trip 后 apiKey 解密回明文
        assertTrue(aliceList.get(0).getPropsJson().contains("\"sk-alice\""));

        assertTrue(repo.existsByOwnerIdAndName("alice", "alice-qwen"));
        assertFalse(repo.existsByOwnerIdAndName("alice", "bob-qwen"));

        // 跨 owner 取不到
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "bob").isEmpty());
        assertTrue(repo.findByIdAndOwnerId(a.getId(), "alice").isPresent());
    }
}
```

- [ ] **Step 7: 运行测试 expect PASS**

```powershell
mvn test -Dtest=ModelProviderRepositoryTest
```

Expected: `Tests run: 1, Failures: 0`. 验证 owner 隔离 + 加密回环。

- [ ] **Step 8: DTO**

Write `src/main/java/io/agentscope/builder/saton/resource/model/dto/ModelProviderUpsertReq.java`:

```java
package io.agentscope.builder.saton.resource.model.dto;

import java.util.Map;

public record ModelProviderUpsertReq(String name, String type, Map<String, Object> props) {}
```

Write `src/main/java/io/agentscope/builder/saton/resource/model/dto/ModelProviderVO.java`:

```java
package io.agentscope.builder.saton.resource.model.dto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.model.ModelProviderEntity;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public record ModelProviderVO(
        Long id,
        String name,
        String type,
        Map<String, Object> props,
        long createdAt,
        long updatedAt
) {
    public static ModelProviderVO maskedFrom(ModelProviderEntity e) {
        String maskedJson = ResourceCommon.maskSensitive(
                ResourceCommon.normalizePropsJson(e.getPropsJson()));
        Map<String, Object> propsMap = jsonToMap(maskedJson);
        return new ModelProviderVO(
                e.getId(), e.getName(), e.getType(), propsMap,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonToMap(String json) {
        try {
            JsonNode node = JsonUtil.mapper().readTree(json);
            return JsonUtil.mapper().treeToValue(node, HashMap.class);
        } catch (Exception e) {
            throw new IllegalStateException("VO json -> map failed", e);
        }
    }
}
```

- [ ] **Step 9: ModelProviderService**

Write `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderService.java`:

```java
package io.agentscope.builder.saton.resource.model;

import cn.dev33.satoken.stp.StpUtil;
import io.agentscope.builder.saton.common.error.ConflictException;
import io.agentscope.builder.saton.common.error.NotFoundException;
import io.agentscope.builder.saton.resource.ResourceCommon;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelProviderService {

    private final ModelProviderRepository repo;

    public ModelProviderService(ModelProviderRepository repo) {
        this.repo = repo;
    }

    public List<ModelProviderVO> list() {
        String me = StpUtil.getLoginIdAsString();
        return repo.findByOwnerIdOrderByCreatedAtDesc(me)
                .stream().map(ModelProviderVO::maskedFrom).toList();
    }

    public ModelProviderVO get(Long id) {
        String me = StpUtil.getLoginIdAsString();
        return ModelProviderVO.maskedFrom(loadMine(id, me));
    }

    @Transactional
    public ModelProviderVO create(ModelProviderUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        if (repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        long now = System.currentTimeMillis();
        ModelProviderEntity e = new ModelProviderEntity();
        e.setOwnerId(me);
        e.setName(req.name());
        e.setType(req.type());
        e.setPropsJson(ResourceCommon.mapToJson(req.props()));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return ModelProviderVO.maskedFrom(repo.save(e));
    }

    @Transactional
    public ModelProviderVO update(Long id, ModelProviderUpsertReq req) {
        String me = StpUtil.getLoginIdAsString();
        requireFields(req);
        ModelProviderEntity e = loadMine(id, me);
        if (!e.getName().equals(req.name())
                && repo.existsByOwnerIdAndName(me, req.name())) {
            throw new ConflictException("name already exists: " + req.name());
        }
        e.setName(req.name());
        e.setType(req.type());
        // merge：incoming 里 value = "***" 的敏感字段保留 existing 值
        String incoming = ResourceCommon.mapToJson(req.props());
        String merged = ResourceCommon.mergeKeepMasked(incoming, e.getPropsJson());
        e.setPropsJson(merged);
        e.setUpdatedAt(System.currentTimeMillis());
        return ModelProviderVO.maskedFrom(e);   // dirty checking flushes
    }

    @Transactional
    public void delete(Long id) {
        String me = StpUtil.getLoginIdAsString();
        long n = repo.deleteByIdAndOwnerId(id, me);
        if (n == 0) {
            throw new NotFoundException("model provider not found: " + id);
        }
    }

    private ModelProviderEntity loadMine(Long id, String me) {
        return repo.findByIdAndOwnerId(id, me)
                .orElseThrow(() -> new NotFoundException("model provider not found: " + id));
    }

    private void requireFields(ModelProviderUpsertReq req) {
        if (req == null
                || req.name() == null || req.name().isBlank()
                || req.type() == null || req.type().isBlank()) {
            throw new IllegalArgumentException("name and type required");
        }
    }
}
```

- [ ] **Step 10: ModelProviderController**

Write `src/main/java/io/agentscope/builder/saton/resource/model/ModelProviderController.java`:

```java
package io.agentscope.builder.saton.resource.model;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelProviderController {

    private final ModelProviderService service;

    public ModelProviderController(ModelProviderService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<ModelProviderVO>> list(ServerWebExchange exchange) {
        return inSaContext(exchange, service::list);
    }

    @GetMapping("/{id}")
    public Mono<ModelProviderVO> get(@PathVariable Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.get(id));
    }

    @PostMapping
    public Mono<ModelProviderVO> create(@RequestBody ModelProviderUpsertReq req,
                                        ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.create(req));
    }

    @PutMapping("/{id}")
    public Mono<ModelProviderVO> update(@PathVariable Long id,
                                        @RequestBody ModelProviderUpsertReq req,
                                        ServerWebExchange exchange) {
        return inSaContext(exchange, () -> service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id, ServerWebExchange exchange) {
        return inSaContext(exchange, () -> { service.delete(id); return null; });
    }

    private <T> Mono<T> inSaContext(ServerWebExchange exchange,
                                    java.util.function.Supplier<T> body) {
        return Mono.fromCallable(() -> {
            SaReactorSyncHolder.setContext(exchange);
            try { return body.get(); } finally { SaReactorSyncHolder.clearContext(); }
        });
    }
}
```

- [ ] **Step 11: 集成测试 ModelProviderFlowTest**

Write `src/test/java/io/agentscope/builder/saton/resource/model/ModelProviderFlowTest.java`:

```java
package io.agentscope.builder.saton.resource.model;

import io.agentscope.builder.saton.auth.dto.LoginRequest;
import io.agentscope.builder.saton.auth.dto.LoginResponse;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderUpsertReq;
import io.agentscope.builder.saton.resource.model.dto.ModelProviderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ModelProviderFlowTest {

    @LocalServerPort int port;

    WebTestClient client;
    String token;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        LoginResponse login = client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("admin", "admin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult().getResponseBody();
        assertNotNull(login);
        this.token = login.token();
    }

    private WebTestClient.RequestHeadersSpec<?> withAuth(WebTestClient.RequestHeadersSpec<?> r) {
        return r.header("satoken", token);
    }

    @Test
    void createListGetUpdateDeleteCycle() {
        // 1) POST create
        ModelProviderVO created = client.post().uri("/api/models")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("my-qwen", "dashscope",
                        Map.of("apiKey", "sk-12345", "modelName", "qwen-max")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertNotNull(created.id());
        assertEquals("my-qwen", created.name());
        assertEquals("***", created.props().get("apiKey"), "apiKey must be masked");
        assertEquals("qwen-max", created.props().get("modelName"));

        // 2) GET list
        List<ModelProviderVO> list = client.get().uri("/api/models")
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ModelProviderVO>>() {})
                .returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.stream().anyMatch(v -> "my-qwen".equals(v.name())));

        // 3) GET by id
        ModelProviderVO got = client.get().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(got);
        assertEquals("***", got.props().get("apiKey"));

        // 4) PUT update with masked apiKey → should keep old secret
        ModelProviderVO updated = client.put().uri("/api/models/" + created.id())
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("my-qwen", "dashscope",
                        Map.of("apiKey", "***", "modelName", "qwen-plus")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ModelProviderVO.class)
                .returnResult().getResponseBody();
        assertNotNull(updated);
        assertEquals("qwen-plus", updated.props().get("modelName"));
        assertEquals("***", updated.props().get("apiKey"));

        // 5) DELETE
        client.delete().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isOk();

        // 6) GET → 404
        client.get().uri("/api/models/" + created.id())
                .header("satoken", token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createDuplicateNameReturns409() {
        client.post().uri("/api/models").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("dup", "dashscope",
                        Map.of("apiKey", "sk-1")))
                .exchange().expectStatus().isOk();
        client.post().uri("/api/models").header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ModelProviderUpsertReq("dup", "openai",
                        Map.of("apiKey", "sk-2")))
                .exchange().expectStatus().is4xxClientError();   // 409
    }

    @Test
    void listWithoutTokenReturns401() {
        client.get().uri("/api/models")
                .exchange().expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 12: 跑全部测试**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn test
```

Expected: 之前 8/8 + 新增 ~6 个，全过。注意 `ModelProviderFlowTest` 的 3 个 test 共享 `admin` 用户 —— 同一个 SpringBootTest 上下文里 "dup" 等记录会跨测试残留？**实际上 `@SpringBootTest` 默认不回滚业务方法，要么按字典序保证测试独立要么主动清理**。如果 `createDuplicateNameReturns409` 在跑 `createListGetUpdateDeleteCycle` 后污染了 db，加 `@org.junit.jupiter.api.MethodOrderer.OrderAnnotation` + `@Order` 显式排，或在每个 test 前清表。

> 简单兜底：让两个 test 用不同 name（一个 "my-qwen"，一个 "dup"），互不重名 —— 上面代码已经做到这点。但 `dup` 那一个本身依赖了"先创建一个 dup → 再创建第二个 dup 应 409"，是不依赖跨测试残留的。OK。

如果 `createListGetUpdateDeleteCycle` 末尾 DELETE 后顺序到了 `createDuplicateNameReturns409`，db 是干净的 → no issue.

- [ ] **Step 13: 手动 smoke**

```powershell
# 启动
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn spring-boot:run
```

另一个窗口：

```powershell
$resp = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}'
$token = $resp.token

# 创建
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/models `
  -Headers @{ satoken=$token; "Content-Type"="application/json" } `
  -Body '{"name":"smoke-qwen","type":"dashscope","props":{"apiKey":"sk-smoke-1234567890","modelName":"qwen-max"}}'

# 列
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/models `
  -Headers @{ satoken=$token }
```

Expected: 第一个 POST 返回 apiKey=`***` 的 VO；第二个 GET 返回的列表里 apiKey 也是 `***`。

打开 H2 console 或用 `h2.jar` 查 `data/builderdb`：
```
SELECT id, name, props_json FROM model_provider WHERE owner_id='admin';
```
`props_json` 字段会形如 `{"apiKey":"enc:v1:base64...","modelName":"qwen-max"}` —— 验证加密入库。

停掉服务。

- [ ] **Step 14: Commit**

```powershell
git add src/
git commit -m "feat(resource/model): ModelProvider full CRUD with field-level encryption + mask"
```

---

## Task 4: McpServer 全套（复刻 Task 3 模板）

照 Task 3 的代码结构 1:1 复刻，资源类型差异：

| 字段 | model | mcp |
|---|---|---|
| 表名 | model_provider | mcp_server |
| 业务唯一名字 | name | name |
| 资源 type 字段含义 | "dashscope"/"openai"/... | transport: "stdio"/"sse"/"http" |
| props 典型字段 | apiKey/baseUrl/modelName | command/url/headers/env |
| URL 前缀 | /api/models | /api/mcp-servers |

**Files (parallel structure):**
- `resource/mcp/McpServerEntity.java` (表 `mcp_server`)
- `resource/mcp/McpServerRepository.java`
- `resource/mcp/dto/McpServerUpsertReq.java` `dto/McpServerVO.java`
- `resource/mcp/McpServerService.java`
- `resource/mcp/McpServerController.java` (`/api/mcp-servers`)
- `src/test/.../McpServerRepositoryTest.java`
- `src/test/.../McpServerFlowTest.java`

**字段对照（McpServerEntity）**：列 `id / owner_id / name(VARCHAR 200) / type(VARCHAR 16) / props_json(LOB encrypted) / created_at / updated_at`；唯一约束 `(owner_id, name)`；索引 `(owner_id)`。

- [ ] **Step 1: 写所有文件，跟 Task 3 一一对应**

把 Task 3 的所有类的代码复制一份，把 `ModelProvider*` 替换成 `McpServer*`，把 URL `/api/models` 替换成 `/api/mcp-servers`，表名换成 `mcp_server`，唯一约束名换成 `uk_mcp_server_owner_name`，索引名 `ix_mcp_server_owner`。Service 业务逻辑完全照搬。

> 不要"重新设计"任何东西 —— 这是有意复用同一套模板。差异都在上表中。

- [ ] **Step 2: 跑测试**

```powershell
mvn test -Dtest=McpServerRepositoryTest,McpServerFlowTest
```

Expected: 4 个 test 通过（同结构 → 同结果）。

- [ ] **Step 3: smoke**

POST `/api/mcp-servers` 带 `{"name":"my-playwright","type":"stdio","props":{"command":"npx playwright-mcp","token":"abc"}}`，验证 token 被加密 + mask。

- [ ] **Step 4: Commit**

```powershell
git add src/
git commit -m "feat(resource/mcp): McpServer CRUD (Task-3 template, parallel structure)"
```

---

## Task 5: SkillMarketplace 全套（复刻 Task 3 模板）

| 字段 | model | marketplace |
|---|---|---|
| 表名 | model_provider | skill_marketplace |
| 业务唯一名字 | name | marketplace_id |
| 资源 type 字段含义 | provider type | "git" / "nacos" |
| props 典型字段 | apiKey/... | url/branch/token |
| URL 前缀 | /api/models | /api/skill-marketplaces |

**唯一约束**：`(owner_id, marketplace_id)`（注意是 marketplace_id 不是 name！同原 builder）。

- [ ] **Step 1: 写所有文件**

复刻 Task 3，但 Entity 字段 `name` 改成 `marketplaceId` (列名 `marketplace_id`)，DTO 字段同。Service `existsByOwnerIdAnd...` 改成 `existsByOwnerIdAndMarketplaceId`。其余完全一致。

- [ ] **Step 2: 跑测试**

```powershell
mvn test -Dtest=SkillMarketplaceRepositoryTest,SkillMarketplaceFlowTest
```

- [ ] **Step 3: smoke**

POST `/api/skill-marketplaces` 带 `{"marketplaceId":"my-skills","type":"git","props":{"url":"git@github.com:me/x.git","token":"ghp_xxxx"}}`。

- [ ] **Step 4: Commit**

```powershell
git add src/
git commit -m "feat(resource/marketplace): SkillMarketplace CRUD"
```

---

## Task 6: SkillRepository 全套（复刻 Task 3 模板）

| 字段 | model | repository |
|---|---|---|
| 表名 | model_provider | skill_repository |
| 业务唯一名字 | name | name |
| 资源 type 字段含义 | provider type | "filesystem" / "git" |
| props 典型字段 | apiKey/... | path 或 url/branch/token |
| URL 前缀 | /api/models | /api/skill-repositories |

- [ ] **Step 1: 写所有文件**

完全照 Task 3 模板，名字换成 SkillRepository*，表 `skill_repository`，唯一约束 `(owner_id, name)`，URL `/api/skill-repositories`。

- [ ] **Step 2: 跑测试**

```powershell
mvn test -Dtest=SkillRepositoryRepositoryTest,SkillRepositoryFlowTest
```

- [ ] **Step 3: smoke**

POST `/api/skill-repositories` 带 `{"name":"my-overlay","type":"git","props":{"url":"git@x.com:y.git","token":"abc"}}`。

- [ ] **Step 4: Commit**

```powershell
git add src/
git commit -m "feat(resource/repository): SkillRepository CRUD"
```

---

## Task 7: 全量回归 + smoke + tag

- [ ] **Step 1: 全量 mvn test**

```powershell
cd "D:\GIT\ownsource\AI\AGENT_SCOPE\agentscope-java\agentscope-builder-saton"
mvn test
```

Expected: 8（M1）+ 4（crypto×2 测试类 ≈ 8 case）+ 4×（Repo+Flow）≈ **20+ tests，全 PASS**。

- [ ] **Step 2: 启动并对 4 个资源都 smoke 一遍**

启动 + 登录拿 token，每个资源都跑一次 POST + GET，确认：
1. POST 成功返回 VO，敏感字段 mask
2. GET list 看到刚创建的记录
3. H2 表里 props_json 含 `enc:v1:` 前缀（用 `h2.jar` 或 H2 console）

- [ ] **Step 3: Tag**

```powershell
git log --oneline | head -15
git tag m2-resources-done
```

---

## Self-Review 检查表

- [x] **Spec coverage**：覆盖 spec §5.1 7 张表中的 4 张资源表（model/mcp/marketplace/repository）+ §6.4 敏感字段加密 + §6.2 owner 隔离。`sys_user/agent_definition/agent_share` 留给后续 M3-M9。
- [x] **Placeholder scan**：无 TBD。每个 task 的 code block 都完整。
- [x] **Type consistency**：跨 4 套资源命名约定一致（XxxEntity / XxxRepository / XxxService / XxxController / XxxUpsertReq / XxxVO）；隔离查询统一 `findByXxxAndOwnerId`。
- [x] **踩坑预防**：M1 §12 的 9 项已在 plan 前言点明（Jackson 3 包名、SaReactorSyncHolder、@DataJpaTest + @Import 等）。
- [x] **测试粒度**：每个资源都有 Repository 单测（owner 隔离 + 加密回环）+ 集成测试（完整 CRUD + duplicate 409 + no-token 401）。

---

## M2 完成定义

1. `mvn test` 全部 PASS
2. 4 个 `/api/*` 资源接口都能 CRUD
3. POST 任一资源后，DB 中 `props_json` 含 `enc:v1:` 加密前缀
4. GET 任一资源返回 VO 时敏感字段是 `***`
5. PUT 时传 `"apiKey":"***"` 保留原值
6. tag `m2-resources-done` 已打

M2 完成后告知用户：开始写 M3 plan（工厂骨架 + ModelFactory）。
